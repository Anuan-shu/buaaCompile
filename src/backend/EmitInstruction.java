package backend;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Const.IrConstString;
import midend.LLVM.Instruction.*;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class EmitInstruction {
    private final MipsModule mips;
    private final HashMap<IrValue, Integer> offsetMap;
    // 当前函数的名字（用于拼接跳转标签）
    private final String currentFuncLabel;
    private final HashMap<AllocateInstruction, Integer> allocaArrayOffsets;
    private boolean optimize = false;

    public EmitInstruction(MipsModule mips, HashMap<IrValue, Integer> offsetMap, HashMap<AllocateInstruction, Integer> allocaArrayOffsets, String currentFuncLabel) {
        this.mips = mips;
        this.offsetMap = offsetMap;
        this.allocaArrayOffsets = allocaArrayOffsets;
        this.currentFuncLabel = currentFuncLabel;
    }

    // --- 入口方法 ---
    public void emit(Instruction instr, boolean optimize) {
        this.optimize = optimize;
        if (instr instanceof AluInst) {
            emitBinary((AluInst) instr);
        } else if (instr instanceof LoadInstr) {
            emitLoad((LoadInstr) instr);
        } else if (instr instanceof StoreInstr) {
            emitStore((StoreInstr) instr);
        } else if (instr instanceof BranchInstr) {
            emitBr((BranchInstr) instr);
        } else if (instr instanceof JumpInstr) {
            emitJump((JumpInstr) instr);
        } else if (instr instanceof CmpInstr) {
            emitIcmp((CmpInstr) instr);
        } else if (instr instanceof CallInstr) {
            emitCall((CallInstr) instr);
        } else if (instr instanceof ReturnInstr) {
            emitRet((ReturnInstr) instr);
        } else if (instr instanceof AllocateInstruction) {
            emitAlloca((AllocateInstruction) instr);
        } else if (instr instanceof ZextInstr) {
            emitZext((ZextInstr) instr);
        } else if (instr instanceof GepInstr) {
            emitGEP((GepInstr) instr);
        } else if (instr instanceof PrintIntInstr) {
            loadToReg(((PrintIntInstr) instr).getPrintValue(), "$a0");

            // 2. 设置系统调用号
            mips.addInst("li $v0, 1");

            // 3. 执行系统调用
            mips.addInst("syscall");
        } else if (instr instanceof PrintStrInstr) {
            // loadToReg 内部检测到是 IrGlobalValue 会自动生成 "la $a0, label"
            loadToReg(((PrintStrInstr) instr).getPrintValue(), "$a0");
            mips.addInst("li $v0, 4");
            mips.addInst("syscall");
        } else if (instr instanceof TruncInstr) {
            loadToReg(((TruncInstr) instr).getOperand(0), "$t0");

            if (instr.irType.isInt1()) {
                // i32 -> i1: 只保留最低 1 位
                mips.addInst("andi $t0, $t0, 1");
            } else if (instr.irType.isInt8()) {
                // i32 -> i8: 只保留低 8 位 (0xFF = 255)
                mips.addInst("andi $t0, $t0, 255");
            }
            saveReg(instr, "$t0");
        }
        // 其他指令
    }

    /**
     * 将 IrValue 的值加载到 MIPS 寄存器 reg 中
     */
    private void loadToReg(IrValue val, String reg) {
        if (val instanceof IrConstInt) {
            // 情况1: 立即数 -> li $t0, 10
            int imm = ((IrConstInt) val).getValue();
            mips.addInst(String.format("li %s, %d", reg, imm));
        } else if (val instanceof IrGlobalValue) {
            // 情况2: 全局变量（本质是地址） -> la $t0, label
            String label = val.irName.substring(1); // 去掉 @
            mips.addInst(String.format("la %s, %s", reg, label));
        } else if (val instanceof IrConstString) {
            // 情况3: 字符串常量 -> la $t0, label
            // 字符串常量也是放在 .data 段的，需要加载其标签地址
            String label = val.irName.substring(1); // 去掉 @
            mips.addInst(String.format("la %s, %s", reg, label));

        } else {
            // 情况4: 局部变量/临时变量 -> lw $t0, offset($fp)
            Integer offset = offsetMap.get(val);
            if (offset == null) {
                System.err.println("Error: Value not found in stack map: " + val.irName);
                return;
            }
            if (offset >= -32768 && offset <= 32767) {
                mips.addInst(String.format("lw %s, %d($fp)", reg, offset));
            } else {
                // 如果超限，需要拆分指令
                // 1. 加载偏移量到 $at (汇编保留寄存器，或者找个临时寄存器如 $t8)
                mips.addInst("li $at, " + offset);
                // 2. 计算绝对地址: $at = $at + $fp
                mips.addInst("addu $at, $at, $fp");
                // 3. 读取: lw $t0, 0($at)
                mips.addInst(String.format("lw %s, 0($at)", reg));
            }
        }
    }

    /**
     * 将寄存器 reg 的值保存回 IrValue 对应的栈位置
     */
    private void saveReg(IrValue dest, String reg) {
        Integer offset = offsetMap.get(dest);
        if (offset != null) {
            if (offset >= -32768 && offset <= 32767) {
                mips.addInst(String.format("sw %s, %d($fp)", reg, offset));
            } else {
                mips.addInst("li $at, " + offset);
                mips.addInst("addu $at, $at, $fp");
                mips.addInst(String.format("sw %s, 0($at)", reg));
            }
        }
    }

    // 辅助函数：判断是否为2的幂
    private int getLog2(int val) {
        if (val > 0 && (val & (val - 1)) == 0) {
            return Integer.numberOfTrailingZeros(val);
        }
        return -1;
    }

    // 尝试优化乘法 (x * const)
    // 返回 true 表示已优化并生成了指令，false 表示放弃优化
    private boolean tryOptimizeMul(IrValue src, int constVal, String dstReg) {
        // 0, 1, 2的幂次 已经在之前处理了，这里处理其他常见常数
        // 使用 $at 作为临时寄存器

        //loadToReg(src, "$t0"); // 源在 $t0

        switch (constVal) {
            case 3: // x * 3 = (x << 1) + x
                mips.addInst("sll $at, $t0, 1");
                mips.addInst("addu " + dstReg + ", $at, $t0");
                return true;
            case 5: // x * 5 = (x << 2) + x
                mips.addInst("sll $at, $t0, 2");
                mips.addInst("addu " + dstReg + ", $at, $t0");
                return true;
            case 6: // x * 6 = (x * 3) << 1
                mips.addInst("sll $at, $t0, 1");
                mips.addInst("addu $at, $at, $t0");
                mips.addInst("sll " + dstReg + ", $at, 1");
                return true;
            case 7: // x * 7 = (x << 3) - x
                mips.addInst("sll $at, $t0, 3");
                mips.addInst("subu " + dstReg + ", $at, $t0");
                return true;
            case 9: // x * 9 = (x << 3) + x
                mips.addInst("sll $at, $t0, 3");
                mips.addInst("addu " + dstReg + ", $at, $t0");
                return true;
            case 10: // x * 10 = (x << 3) + (x << 1)
                mips.addInst("sll $at, $t0, 3");
                mips.addInst("sll " + dstReg + ", $t0, 1");
                mips.addInst("addu " + dstReg + ", $at, " + dstReg);
                return true;
            // 可以继续添加 11, 12, ... 只要指令数 < 5 即可
            default:
                return false;
        }
    }

    // 尝试优化除法 (x / const)
    // 使用乘法逆元法： div -> mult (high) + shift + sign_fix
    private boolean tryOptimizeDiv(IrValue src, int d, String dstReg) {
        // 只处理常用的小正整数除数，避免魔数计算太复杂
        long magic;
        int shift;

        // 查表 (Hacker's Delight)
        switch (d) {
            case 3:
                magic = 0x55555556L;
                shift = 0;
                break;
            case 5:
                magic = 0x66666667L;
                shift = 1;
                break;
            case 6:
                magic = 0x2AAAAAABL;
                shift = 0;
                break;
            case 10:
                magic = 0x66666667L;
                shift = 2;
                break;
            case 100:
                magic = 0x51EB851FL;
                shift = 5;
                break;
            default:
                return false; // 其他数暂不优化
        }

        //loadToReg(src, "$t0"); // 被除数

        // 1. 加载魔数
        // 魔数通常很大，可能超过16位，la/li 伪指令会自动拆分
        // 按十六进制字符串加载
        mips.addInst(String.format("li $at, 0x%X", magic));

        // 2. 有符号乘法 (High part -> hi)
        mips.addInst("mult $t0, $at");
        mips.addInst("mfhi $at"); // 取高32位

        // 3. 算术右移
        if (shift > 0) {
            mips.addInst(String.format("sra $at, $at, %d", shift));
        }

        // 4. 符号位修正 (t0 < 0 ? result++ : result)
        // 获取符号位 (x >>> 31)
        mips.addInst("srl $t1, $t0, 31");

        // 结果加上符号位
        mips.addInst("addu " + dstReg + ", $at, $t1");

        return true;
    }

    // 1. 二元运算 (Add, Sub, Mul, Div, Rem...)
    private void emitBinary(AluInst instr) {
        loadToReg(instr.getLeft(), "$t0");
        loadToReg(instr.getRight(), "$t1");

        boolean optimized = false;
        // 检查右操作数是否为常数
        if (instr.getRight() instanceof IrConstInt) {
            int val = ((IrConstInt) instr.getRight()).getValue();
            int log2 = getLog2(val);

            // 优化乘法和除法的 2 的幂次
            if (log2 >= 0) {
                if (instr.getOp().equals("MUL")) {
                    // x * 2^k  ==>  x << k
                    mips.addInst(String.format("sll $t2, $t0, %d", log2));
                    optimized = true;
                } else if (instr.getOp().equals("SDIV")) {
                    // 优化：x / 2^k
                    // 必须处理负数向零取整的问题
                    // 逻辑：if (x < 0) x = x + (2^k - 1); result = x >> k;

                    if (log2 > 0) {
                        // 1. 取出符号位到 $t3 (如果 x<0，$t3=-1; 否则 $t3=0)
                        mips.addInst("sra $t3, $t0, 31");

                        // 2. 生成偏置 (2^k - 1)
                        // 利用符号位移位: 如果 $t3 是全1，逻辑右移 (32-k) 位后，低 k 位为 1
                        mips.addInst(String.format("srl $t3, $t3, %d", 32 - log2));

                        // 3. 加上偏置: $t3 = x + bias
                        mips.addInst("addu $t3, $t0, $t3");

                        // 4. 算术右移
                        mips.addInst(String.format("sra $t2, $t3, %d", log2));

                    } else {
                        // 如果 log2 == 0 (即除以 1)，直接移动
                        mips.addInst("move $t2, $t0");
                    }
                    optimized = true;
                }
            }

            // 进一步尝试其他常数优化
            if (!optimized) {
                if (instr.getOp().equals("MUL")) {
                    optimized = tryOptimizeMul(instr.getLeft(), val, "$t2");
                } else if (instr.getOp().equals("SDIV")) {
                    // 这里只优化除数 > 0 的情况
                    if (val > 0) {
                        optimized = tryOptimizeDiv(instr.getLeft(), val, "$t2");
                    }
                }
            }
        }
        if (!optimized) {
            switch (instr.getOp()) {
                case "ADD":
                    mips.addInst("addu $t2, $t0, $t1");
                    break;
                case "SUB":
                    mips.addInst("subu $t2, $t0, $t1");
                    break;
                case "MUL":
                    mips.addInst("mul $t2, $t0, $t1");
                    break;
                case "SDIV": // 有符号除法
                    mips.addInst("div $t0, $t1");
                    mips.addInst("mflo $t2"); // 取商
                    break;
                case "SREM": // 取模 (remainder)
                    mips.addInst("div $t0, $t1");
                    mips.addInst("mfhi $t2"); // 取余
                    break;
                case "AND":
                    mips.addInst("and $t2, $t0, $t1");
                    break;
                case "OR":
                    mips.addInst("or $t2, $t0, $t1");
                    break;
                default:
                    throw new RuntimeException("Unknown Binary Op: " + instr.getOp());
            }
        }
        saveReg(instr, "$t2");
    }

    // 2. Load 指令: %val = load i32, i32* %ptr
    private void emitLoad(LoadInstr instr) {
        IrValue ptr = instr.getPtr();
        // ptr 是一个指针（地址）。
        // 如果 ptr 是全局变量，loadToReg 会生成 la $t1, label
        // 如果 ptr 是局部变量(alloca出来的)，loadToReg 会从栈里把它存的地址读到 $t1
        loadToReg(ptr, "$t1");

        mips.addInst("lw $t0, 0($t1)"); // 从地址 $t1 处读取真实的值
        saveReg(instr, "$t0");          // 把值存入 %val 的栈槽
    }

    // 3. Store 指令: store i32 %val, i32* %ptr
    private void emitStore(StoreInstr instr) {
        IrValue val = instr.getVal();
        IrValue ptr = instr.getPtr();

        loadToReg(val, "$t0"); // 加载要存储的数据
        loadToReg(ptr, "$t1"); // 加载目标地址

        mips.addInst("sw $t0, 0($t1)"); // 写入内存
    }

    // 4. cmp 指令
    // MIPS 没有类似 LLVM 的 i1 类型，用 0 和 1 整数表示
    private void emitIcmp(CmpInstr instr) {
        loadToReg(instr.getLeft(), "$t0");
        loadToReg(instr.getRight(), "$t1");

        // 目标：若条件成立，$t2 = 1，否则 $t2 = 0
        switch (instr.getOp()) {
            case "EQ":  // ==
                mips.addInst("xor $t2, $t0, $t1"); // 若相等，异或为0
                mips.addInst("sltiu $t2, $t2, 1");  // 若 $t2 < 1 (即为0)，则置1
                break;
            case "NE":  // !=
                mips.addInst("xor $t2, $t0, $t1");
                mips.addInst("sltu $t2, $zero, $t2"); // 若 0 < $t2，则置1
                break;
            case "GT": // >
                mips.addInst("slt $t2, $t1, $t0"); // if t1 < t0, t2 = 1
                break;
            case "GE": // >=  (t0 >= t1  <==>  !(t0 < t1))
                mips.addInst("slt $t2, $t0, $t1"); // check t0 < t1
                mips.addInst("xori $t2, $t2, 1");  // 取反
                break;
            case "LT": // <
                mips.addInst("slt $t2, $t0, $t1");
                break;
            case "LE": // <= (t0 <= t1 <==> !(t1 < t0))
                mips.addInst("slt $t2, $t1, $t0");
                mips.addInst("xori $t2, $t2, 1");
                break;
            default:
                break;
        }
        saveReg(instr, "$t2");
    }

    // 5. Branch 指令
    private void emitBr(BranchInstr instr) {

        // 条件跳转: br i1 %cond, label %true, label %false
        loadToReg(instr.getCond(), "$t0");

        // 获取 Block 的纯名字 (去掉 function名等前缀)
        String trueLabel = currentFuncLabel + "_" + instr.getTrueBlock().irName;
        String falseLabel = currentFuncLabel + "_" + instr.getFalseBlock().irName;

        // 如果 cond != 0 (true)，跳转 trueLabel

        String skipLabel = "skip_" + UUID.randomUUID().toString().replace("-", "");
        // 如果 $t0 == 0 (即不满足条件)，跳过 j 指令
        mips.addInst("beqz $t0, " + skipLabel);
        mips.addInst("nop");
        // 只有满足条件才执行这个长跳转
        mips.addInst("j " + trueLabel);
        mips.addInst("nop");
        mips.addInst(skipLabel + ":");

        // 否则跳转 falseLabel
        mips.addInst("j " + falseLabel);
        mips.addInst("nop");

    }

    private void emitJump(JumpInstr instr) {
        // 无条件跳转: j label %target
        String targetLabel = currentFuncLabel + "_" + instr.getTargetBlock().irName;
        mips.addInst("j " + targetLabel);
        mips.addInst("nop");
    }

    // 6. Call 指令
    private void emitCall(CallInstr instr) {
        // 6.1 准备参数
        ArrayList<IrValue> args = instr.getParameters();

        int stackArgs = Math.max(0, args.size() - 4);

        // 1. 如果有栈参数，先腾出空间！防止覆盖局部变量
        if (stackArgs > 0) {
            mips.addInst("addiu $sp, $sp, -" + (stackArgs * 4));
        }

        for (int i = 0; i < args.size(); i++) {
            IrValue arg = args.get(i);
            if (i < 4) {
                // 前4个参数 -> $a0 - $a3
                loadToReg(arg, "$a" + i);
            } else {
                // >4个参数 -> 压栈 (存到 $sp + 4*offset)
                // 注意：这里是对当前 $sp 的相对偏移，参数区通常在栈顶底部
                loadToReg(arg, "$t0");
                mips.addInst(String.format("sw $t0, %d($sp)", (i - 4) * 4));
                // 注意：如果你的栈帧设计预留了 caller argument area，这里直接写
                // 如果没有动态 sub $sp，需要确认 $sp 指向的位置
            }
        }

        // 6.2 跳转
        String funcName = instr.getTargetFunction().irName.replace("@", "");
        mips.addInst("jal " + funcName);
        mips.addInst("nop");

        // 调用完恢复栈指针
        if (stackArgs > 0) {
            mips.addInst("addiu $sp, $sp, " + (stackArgs * 4));
        }

        // 6.3 处理返回值
        // 如果 call 指令有返回值 (不是 void)，则结果在 $v0
        if (!instr.irType.isVoid()) {
            saveReg(instr, "$v0");
        }
    }

    // 7. Ret 指令
    private void emitRet(ReturnInstr instr) {
        if (!instr.isReturnVoid()) {
            loadToReg(instr.getReturnValue(), "$v0");
        }
        if (currentFuncLabel.equals("main")) {
            // 如果是 main 函数，执行 syscall 10 (exit) 终止程序
            mips.addInst("li $v0, 10");
            mips.addInst("syscall");
        } else {
            // 如果是普通函数，执行标准的栈帧恢复和返回
            mips.addInst("move $sp, $fp");    // 恢复 sp
            mips.addInst("lw $fp, -8($sp)");  // 恢复 fp
            mips.addInst("lw $ra, -4($sp)");  // 恢复 ra
            mips.addInst("jr $ra");           // 返回调用者
            mips.addInst("nop");
        }
    }

    // 8. Alloca 指令
    // %ptr = alloca i32
    private void emitAlloca(AllocateInstruction instr) {
        // 计算数组实体的绝对地址，并存入指针变量

        // 1. 获取数组实体在栈上的偏移 ($fp + entityOffset)
        Integer entityOffset = allocaArrayOffsets.get(instr);
        if (entityOffset == null) return;

        // 2. 计算绝对地址 -> $t0
        //mips.addInst("addiu $t0, $fp, " + entityOffset);
        if (entityOffset >= -32768 && entityOffset <= 32767) {
            mips.addInst("addiu $t0, $fp, " + entityOffset);
        } else {
            // 偏移量太大，必须用寄存器中转
            mips.addInst("li $at, " + entityOffset);
            mips.addInst("addu $t0, $fp, $at");
        }
        // 3. 将地址存入指针变量的位置 (instr 在 offsetMap 中的位置)
        // 复用 saveReg ，把 $t0 存入 instr 对应的栈槽
        saveReg(instr, "$t0");
    }

    // 9. Zext (零扩展) i1 -> i32
    private void emitZext(ZextInstr instr) {
        loadToReg(instr.getOperand(0), "$t0");
        // i1 在实现里已经是 0/1 的整数，所以 zext 就是 move
        // 为了保险，做个与操作
        mips.addInst("andi $t0, $t0, 1");
        saveReg(instr, "$t0");
    }

    // 10. GEP (数组地址计算)
    // %ptr = getelementptr [5 x i32], [5 x i32]* %base, i32 0, i32 %idx
    private void emitGEP(GepInstr instr) {
        IrValue base = instr.getPtr();
        IrValue index = instr.getIndice(); // 取最后一个索引

        loadToReg(base, "$t0");  // 基地址
        loadToReg(index, "$t1"); // 索引

        // 计算偏移: index * 4 (假设是 i32 数组)
        mips.addInst("sll $t1, $t1, 2"); // $t1 = $t1 * 4

        // 地址相加
        mips.addInst("addu $t2, $t0, $t1");

        saveReg(instr, "$t2");
    }
}