package backend;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Const.IrConstString;
import midend.LLVM.Instruction.*;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EmitInstruction {
    private final MipsModule mips;
    private final HashMap<IrValue, Integer> offsetMap;
    // 当前函数的名字（用于拼接跳转标签）
    private final String currentFuncLabel;
    private final HashMap<AllocateInstruction, Integer> allocaArrayOffsets;
    private boolean optimize = false;
    private boolean isLeaf = false;

    private final Map<IrValue, Integer> regAllocation;
    private final Map<Integer, Integer> sRegStackOffsets; // 用于 epilogue 恢复

    public EmitInstruction(MipsModule mips,
                           HashMap<IrValue, Integer> offsetMap,
                           HashMap<AllocateInstruction, Integer> allocaArrayOffsets,
                           String currentFuncLabel,
                           Map<IrValue, Integer> regAllocation,
                           Map<Integer, Integer> sRegStackOffsets) {
        this.mips = mips;
        this.offsetMap = offsetMap;
        this.allocaArrayOffsets = allocaArrayOffsets;
        this.currentFuncLabel = currentFuncLabel;
        this.regAllocation = regAllocation;
        this.sRegStackOffsets = sRegStackOffsets;
    }

    // --- 入口方法 ---
    public void emit(Instruction instr, boolean optimize, boolean isLeaf) {
        this.isLeaf = isLeaf;
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
        } else if (instr instanceof PhiInstr) {
            // 什么都不做
            // Phi 的代码生成已经在前驱块的跳转处完成了 (resolvePhiCopies)
        }
        // 其他指令
    }

    /**
     * 将 IrValue 的值加载到 MIPS 寄存器 reg 中
     */
    private void loadToReg(IrValue val, String reg) {
        // 1. 优先检查寄存器分配
        if (regAllocation != null && regAllocation.containsKey(val)) {
            int allocatedIdx = regAllocation.get(val);
            String allocatedRegName = RegisterAllocator.REG_NAMES[allocatedIdx];
            if (!reg.equals(allocatedRegName)) {
                mips.addInst("move " + reg + ", " + allocatedRegName);
            }
            return; // 找到寄存器后立即返回
        }

        // 2. 常量/全局变量
        if (val instanceof IrConstInt) {
            int imm = ((IrConstInt) val).getValue();
            mips.addInst(String.format("li %s, %d", reg, imm));
            return;
        } else if (val instanceof IrGlobalValue) {
            String label = val.irName.substring(1);
            mips.addInst(String.format("la %s, %s", reg, label));
            return;
        } else if (val instanceof IrConstString) {
            String label = val.irName.substring(1);
            mips.addInst(String.format("la %s, %s", reg, label));
            return;
        }

        // 3. 栈加载 (Spill)
        Integer offset = offsetMap.get(val);
        if (offset != null) {
            if (offset >= -32768 && offset <= 32767) {
                mips.addInst(String.format("lw %s, %d($fp)", reg, offset));
            } else {
                mips.addInst("li $at, " + offset);
                mips.addInst("addu $at, $at, $fp");
                mips.addInst(String.format("lw %s, 0($at)", reg));
            }
        } else {
            // 防御性：未分配空间且未分配寄存器 (可能是死代码遗留)
            mips.addInst("li " + reg + ", 0");
        }
    }

    /**
     * 将寄存器 reg 的值保存回 IrValue 对应的栈位置
     */
    private void saveReg(IrValue dest, String srcReg) {
        // 1. 优先检查寄存器分配
        if (regAllocation != null && regAllocation.containsKey(dest)) {
            int allocatedIdx = regAllocation.get(dest);
            String allocatedRegName = RegisterAllocator.REG_NAMES[allocatedIdx];
            // 只有当源和目的不同时才生成 move
            if (!srcReg.equals(allocatedRegName)) {
                mips.addInst("move " + allocatedRegName + ", " + srcReg);
            }
            return;
        }

        // 2. 栈存储
        Integer offset = offsetMap.get(dest);
        if (offset != null) {
            if (offset >= -32768 && offset <= 32767) {
                mips.addInst(String.format("sw %s, %d($fp)", srcReg, offset));
            } else {
                mips.addInst("li $at, " + offset);
                mips.addInst("addu $at, $at, $fp");
                mips.addInst(String.format("sw %s, 0($at)", srcReg));
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
    private boolean tryOptimizeMul(int c, String srcReg, String dstReg) {
        if (c == 0) {
            mips.addInst("move " + dstReg + ", $zero");
            return true;
        }
        if (c == 1) {
            if (!srcReg.equals(dstReg)) mips.addInst("move " + dstReg + ", " + srcReg);
            return true;
        }
        if (c == -1) {
            mips.addInst("subu " + dstReg + ", $zero, " + srcReg);
            return true;
        }

        // 1. 检查 2 的幂次 (x * 2^k -> sll x, k)
        int log2 = getLog2(Math.abs(c));
        if (log2 != -1) {
            String shiftInst = "sll " + dstReg + ", " + srcReg + ", " + log2;
            mips.addInst(shiftInst);
            // 如果是负数幂 (x * -8)，结果取反
            if (c < 0) {
                mips.addInst("subu " + dstReg + ", $zero, " + dstReg);
            }
            return true;
        }

        // 2. 检查常见的小常数 (Cost <= 2 instructions)
        // 利用 $at 或 $t1 作为临时寄存器 (我们在 emitBinary 里只用了 $t0, $t1)
        // 此时 $t1 存的是常数 C，我们即将用移位逻辑代替它，所以 $t1 可以被覆盖作为临时寄存器
        String tmp = "$at";

        switch (c) {
            case 3: // x*3 = (x<<1) + x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 1");
                mips.addInst("addu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 5: // x*5 = (x<<2) + x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 2");
                mips.addInst("addu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 6: // x*6 = (x*3)<<1
                mips.addInst("sll " + tmp + ", " + srcReg + ", 1");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 1");
                return true;
            case 7: // x*7 = (x<<3) - x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 3");
                mips.addInst("subu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 9: // x*9 = (x<<3) + x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 3");
                mips.addInst("addu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 10: // x*10 = (x<<3) + (x<<1) - 需要确保不覆盖 srcReg
                mips.addInst("sll " + tmp + ", " + srcReg + ", 1");
                mips.addInst("sll " + dstReg + ", " + srcReg + ", 3");
                mips.addInst("addu " + dstReg + ", " + dstReg + ", " + tmp);
                return true;
            case 12: // x*12 = x*3 << 2
                mips.addInst("sll " + tmp + ", " + srcReg + ", 1");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 2");
                return true;
            case 15: // x*15 = (x<<4) - x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 4");
                mips.addInst("subu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 17: // x*17 = (x<<4) + x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 4");
                mips.addInst("addu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 18: // x*18 = x*9 << 1
                mips.addInst("sll " + tmp + ", " + srcReg + ", 3");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 1");
                return true;
            case 20: // x*20 = x*5 << 2
                mips.addInst("sll " + tmp + ", " + srcReg + ", 2");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 2");
                return true;
            case 24: // x*24 = x*3 << 3
                mips.addInst("sll " + tmp + ", " + srcReg + ", 1");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 3");
                return true;
            case 31: // x*31 = (x<<5) - x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 5");
                mips.addInst("subu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 33: // x*33 = (x<<5) + x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 5");
                mips.addInst("addu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 36: // x*36 = x*9 << 2
                mips.addInst("sll " + tmp + ", " + srcReg + ", 3");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 2");
                return true;
            case 40: // x*40 = x*5 << 3
                mips.addInst("sll " + tmp + ", " + srcReg + ", 2");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 3");
                return true;
            case 48: // x*48 = x*3 << 4
                mips.addInst("sll " + tmp + ", " + srcReg + ", 1");
                mips.addInst("addu " + tmp + ", " + tmp + ", " + srcReg);
                mips.addInst("sll " + dstReg + ", " + tmp + ", 4");
                return true;
            case 63: // x*63 = (x<<6) - x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 6");
                mips.addInst("subu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            case 65: // x*65 = (x<<6) + x
                mips.addInst("sll " + tmp + ", " + srcReg + ", 6");
                mips.addInst("addu " + dstReg + ", " + tmp + ", " + srcReg);
                return true;
            // 其他大数直接返回 false，走 mul 指令
        }

        return false;
    }

    // 尝试优化除法 (x / const)
    // 使用乘法逆元法： div -> mult (high) + shift + sign_fix
    private boolean tryOptimizeDiv(int c, String srcReg, String dstReg) {
        // 边界情况
        if (c == 1) {
            if (!srcReg.equals(dstReg)) mips.addInst("move " + dstReg + ", " + srcReg);
            return true;
        }
        if (c == -1) {
            mips.addInst("subu " + dstReg + ", $zero, " + srcReg);
            return true;
        }

        // 1. 处理 2 的幂次 (Branchless fix for negative numbers)
        // 算法: x / 2^k
        // if (x < 0) x += (2^k - 1);
        // return x >> k;
        int log2 = getLog2(Math.abs(c));
        if (log2 > 0) {
            int k = log2;
            // 步骤 A: 获取符号位并构造偏置
            // $at = src >> 31 (0 or -1)
            mips.addInst("sra $at, " + srcReg + ", 31");

            // $at = $at >>> (32-k)
            // 如果 src>=0, at=0; 如果 src<0, at = 0...011...1 (共k个1)
            mips.addInst("srl $at, $at, " + (32 - k));

            // 步骤 B: 加上偏置
            // dst = src + at
            mips.addInst("addu " + dstReg + ", " + srcReg + ", $at");

            // 步骤 C: 算术右移
            mips.addInst("sra " + dstReg + ", " + dstReg + ", " + k);

            // 如果除数是负数 (e.g. / -4)，结果取反
            if (c < 0) {
                mips.addInst("subu " + dstReg + ", $zero, " + dstReg);
            }
            return true;
        }

        // 2. 处理特定常数 (Magic Number)
        // 仅处理正除数，负除数情况很少见且容易出错
        if (c > 0) {
            long magic;
            int shift;

            // 查表 (M: Magic, S: Shift)
            // 验证来源: Hacker's Delight / LLVM output
            // 只保留经过验证的常数
            switch (c) {
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
                case 12:
                    magic = 0x2AAAAAABL;
                    shift = 1;
                    break;
                case 20:
                    magic = 0x66666667L;
                    shift = 3;
                    break;
                case 24:
                    magic = 0x2AAAAAABL;
                    shift = 2;
                    break;
                case 40:
                    magic = 0x66666667L;
                    shift = 4;
                    break;
                case 48:
                    magic = 0x2AAAAAABL;
                    shift = 3;
                    break;
                case 100:
                    magic = 0x51EB851FL;
                    shift = 5;
                    break;
                default:
                    return false;
            }

            // 生成代码序列
            // li $at, MAGIC
            mips.addInst(String.format("li $at, 0x%X", magic));

            // mult src, magic
            mips.addInst("mult " + srcReg + ", $at");

            // mfhi $at (高 32 位)
            mips.addInst("mfhi $at");

            // sra $at, $at, shift
            if (shift > 0) {
                mips.addInst("sra $at, $at, " + shift);
            }

            // t1 = src >> 31 (符号位)
            // 注意：我们需要一个临时寄存器，不能覆盖 srcReg ($t0)。
            // 此时 dstReg ($t2) 是空闲的，可以用作临时
            mips.addInst("srl " + dstReg + ", " + srcReg + ", 31");

            // result = at + sign
            mips.addInst("addu " + dstReg + ", $at, " + dstReg);

            return true;
        }

        return false;
    }

    /**
     * 智能获取操作数的寄存器名称
     *
     * @param val         操作数 (IrValue)
     * @param fallbackReg 如果该值不在寄存器中，加载到的临时寄存器 (如 "$t0")
     * @return 实际使用的寄存器名称 (如 "$s1" 或 "$t0")
     */
    private String getOpReg(IrValue val, String fallbackReg) {
        // 1. 检查是否分配了物理寄存器
        if (regAllocation != null && regAllocation.containsKey(val)) {
            return RegisterAllocator.REG_NAMES[regAllocation.get(val)];
        }
        // 2. 如果没分配，加载到 fallbackReg 并返回 fallbackReg
        loadToReg(val, fallbackReg);
        return fallbackReg;
    }

    // 1. 二元运算 (Add, Sub, Mul, Div, Rem...)
    private void emitBinary(AluInst instr) {
        // 1. 智能准备操作数
        // 只有在必要时才使用 $t0, $t1
        String leftReg = getOpReg(instr.getLeft(), "$t0");

        // 2. 确定结果寄存器
        String destReg = "$t2"; // 默认为临时寄存器
        if (regAllocation != null && regAllocation.containsKey(instr)) {
            destReg = RegisterAllocator.REG_NAMES[regAllocation.get(instr)];
        }

        boolean optimized = false;

        // 3. 尝试算术优化
        if (instr.getRight() instanceof IrConstInt) {
            int val = ((IrConstInt) instr.getRight()).getValue();

            if (instr.getOp().equals("MUL")) {
                // 把动态的寄存器名传进去
                optimized = tryOptimizeMul(val, leftReg, destReg);
            } else if (instr.getOp().equals("SDIV")) {
                if (val != 0) {
                    optimized = tryOptimizeDiv(val, leftReg, destReg);
                }
            } else if (instr.getOp().equals("SREM")) {
                // 公式: x % c = x - (x / c) * c
                if (val != 0) {
                    // 一个临时寄存器来存商
                    // destReg 最终存余数，leftReg 存原数 x
                    // 借用 $v1 作为临时寄存器
                    String tempReg = "$v1";

                    // 第一步：计算 quotient = x / c
                    if (tryOptimizeDiv(val, leftReg, tempReg)) {
                        // 计算 product = quotient * c
                        boolean mulOptimized = tryOptimizeMul(val, tempReg, tempReg);
                        // 乘法没能优化，回退到硬件乘法
                        if (!mulOptimized) {
                            mips.addInst("li $at, " + val);
                            mips.addInst("mul " + tempReg + ", " + tempReg + ", $at");
                        }

                        // 第三步：计算 remainder = x - product
                        mips.addInst(String.format("subu %s, %s, %s", destReg, leftReg, tempReg));

                        optimized = true;
                    }
                }
            }
        }

        //  I-Type 指令优化
        // 如果没有被 Mul/Div 优化处理过，且右操作数是常数
        if (!optimized && instr.getRight() instanceof IrConstInt) {
            int val = ((IrConstInt) instr.getRight()).getValue();

            // 检查是否在 16 位有符号整数范围内 (-32768 ~ 32767)
            boolean is16Bit = (val >= -32768 && val <= 32767);
            boolean is16BitUnsigned = (val >= 0 && val <= 65535);

            switch (instr.getOp()) {
                case "ADD":
                    if (is16Bit) {
                        mips.addInst(String.format("addiu %s, %s, %d", destReg, leftReg, val));
                        optimized = true;
                    }
                    break;
                case "SUB":
                    // x - C <=> x + (-C)
                    if (val >= -32767 && val <= 32768) { // 取反后要在 range 内
                        mips.addInst(String.format("addiu %s, %s, %d", destReg, leftReg, -val));
                        optimized = true;
                    }
                    break;
                case "AND":
                    if (is16BitUnsigned) {
                        mips.addInst(String.format("andi %s, %s, %d", destReg, leftReg, val));
                        optimized = true;
                    }
                    break;
                case "OR":
                    if (is16BitUnsigned) {
                        mips.addInst(String.format("ori %s, %s, %d", destReg, leftReg, val));
                        optimized = true;
                    }
                    break;
                case "XOR":
                    if (is16BitUnsigned) {
                        mips.addInst(String.format("xori %s, %s, %d", destReg, leftReg, val));
                        optimized = true;
                    }
                    break;
            }
        }

        // 4. 标准硬件指令
        if (!optimized) {
            String rightReg = getOpReg(instr.getRight(), "$t1");
            switch (instr.getOp()) {
                case "ADD":
                    mips.addInst(String.format("addu %s, %s, %s", destReg, leftReg, rightReg));
                    break;
                case "SUB":
                    mips.addInst(String.format("subu %s, %s, %s", destReg, leftReg, rightReg));
                    break;
                case "MUL":
                    mips.addInst(String.format("mul %s, %s, %s", destReg, leftReg, rightReg));
                    break;
                case "SDIV":
                    // div 指令的结果在 LO/HI 寄存器，不接受目标寄存器参数
                    mips.addInst(String.format("div %s, %s", leftReg, rightReg));
                    mips.addInst("mflo " + destReg);
                    break;
                case "SREM":
                    mips.addInst(String.format("div %s, %s", leftReg, rightReg));
                    mips.addInst("mfhi " + destReg);
                    break;
                case "AND":
                    mips.addInst(String.format("and %s, %s, %s", destReg, leftReg, rightReg));
                    break;
                case "OR":
                    mips.addInst(String.format("or %s, %s, %s", destReg, leftReg, rightReg));
                    break;
                case "XOR":
                    mips.addInst(String.format("xor %s, %s, %s", destReg, leftReg, rightReg));
                    break;
                default:
                    throw new RuntimeException("Unknown Binary Op: " + instr.getOp());
            }
        }

        // 5. 保存/回写结果
        // 如果 destReg 已经是分配好的寄存器，saveReg 内部会检测到 src==dst 而跳过 move
        // 如果 destReg 是 $t2 且变量被 Spill 了，saveReg 会生成 sw
        saveReg(instr, destReg);
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

        IrBasicBlock currentBlock = (IrBasicBlock) instr.getParent();
        IrBasicBlock trueBlock = instr.getTrueBlock();
        IrBasicBlock falseBlock = instr.getFalseBlock();
        String trueName = trueBlock.irName.replace("@", "");
        String falseName = falseBlock.irName.replace("@", "");
        // 获取 Block 的纯名字 (去掉 function名等前缀)
        String trueLabel = currentFuncLabel + "_" + trueName;
        String falseLabel = currentFuncLabel + "_" + falseName;

        // 如果 cond != 0 (true)，跳转 trueLabel

        String skipLabel = "skip_" + UUID.randomUUID().toString().replace("-", "");
        // 如果 $t0 == 0 (即不满足条件)，跳过 j 指令
        mips.addInst("beqz $t0, " + skipLabel);
        mips.addInst("nop");

        // 处理 True 块的 Phi
        resolvePhiCopies(trueBlock, currentBlock);

        // 只有满足条件才执行这个长跳转
        mips.addInst("j " + trueLabel);
        mips.addInst("nop");

        mips.addInst(skipLabel + ":");

        // 处理 False 块的 Phi
        resolvePhiCopies(falseBlock, currentBlock);

        // 否则跳转 falseLabel
        mips.addInst("j " + falseLabel);
        mips.addInst("nop");

    }

    private void emitJump(JumpInstr instr) {
        // 获取当前块和目标块
        IrBasicBlock currentBlock = (IrBasicBlock) instr.getParent();
        IrBasicBlock targetBlock = instr.getTargetBlock();

        // 处理 Phi Copy
        resolvePhiCopies(targetBlock, currentBlock);

        String targetName = targetBlock.irName.replace("@", "");
        // 无条件跳转: j label %target
        String targetLabel = currentFuncLabel + "_" + targetName;
        mips.addInst("j " + targetLabel);
        mips.addInst("nop");
    }

    // 6. Call 指令
    private void emitCall(CallInstr instr) {
        ArrayList<IrValue> args = instr.getParameters();
        int stackArgs = Math.max(0, args.size() - 4);
        // 栈对齐 (8字节)
        if (stackArgs * 4 % 8 != 0) stackArgs++;

        if (stackArgs > 0) mips.addInst("addiu $sp, $sp, -" + (stackArgs * 4));

        for (int i = 0; i < args.size(); i++) {
            IrValue arg = args.get(i);
            if (i < 4) {
                loadToReg(arg, "$a" + i);
            } else {
                loadToReg(arg, "$t0");
                mips.addInst(String.format("sw $t0, %d($sp)", (i - 4) * 4));
            }
        }

        String funcName = instr.getTargetFunction().irName.replace("@", "");
        mips.addInst("jal " + funcName);
        mips.addInst("nop");

        if (stackArgs > 0) mips.addInst("addiu $sp, $sp, " + (stackArgs * 4));
        if (!instr.irType.isVoid()) saveReg(instr, "$v0");
    }

    // 7. Ret 指令
    private void emitRet(ReturnInstr instr) {
        if (!instr.isReturnVoid()) loadToReg(instr.getReturnValue(), "$v0");

        if (currentFuncLabel.equals("main")) {
            mips.addInst("li $v0, 10");
            mips.addInst("syscall");
        } else {
            // 恢复 $s 寄存器
            if (sRegStackOffsets != null) {
                for (int i = RegisterAllocator.CALLEE_SAVED_START; i <= RegisterAllocator.CALLEE_SAVED_END; i++) {
                    if (sRegStackOffsets.containsKey(i)) {
                        mips.addInst(String.format("lw %s, %d($fp)",
                                RegisterAllocator.REG_NAMES[i], sRegStackOffsets.get(i)));
                    }
                }
            }
            mips.addInst("move $sp, $fp");
            mips.addInst("lw $fp, -8($sp)");
            if (!isLeaf) {
                mips.addInst("lw $ra, -4($sp)");
            }
            mips.addInst("jr $ra");
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

    /**
     * 处理 Phi 消除：在跳转到 targetBlock 之前，将当前块流向 targetBlock 所需的值存入 Phi 的栈槽。
     */
    private void resolvePhiCopies(IrBasicBlock targetBlock, IrBasicBlock currentBlock) {
        ArrayList<PhiInstr> phis = new ArrayList<>();
        for (Instruction instr : targetBlock.getInstructions()) {
            if (instr instanceof PhiInstr) phis.add((PhiInstr) instr);
            else break;
        }
        if (phis.isEmpty()) return;

        // 1. 压栈
        for (PhiInstr phi : phis) {
            ArrayList<IrBasicBlock> blocks = phi.getIncomingBlocks();
            ArrayList<IrValue> values = phi.getIncomingValues();
            for (int i = 0; i < blocks.size(); i++) {
                if (blocks.get(i) == currentBlock) {
                    loadToReg(values.get(i), "$t0");
                    mips.addInst("addiu $sp, $sp, -4");
                    mips.addInst("sw $t0, 0($sp)");
                    break;
                }
            }
        }
        // 2. 弹栈
        for (int i = phis.size() - 1; i >= 0; i--) {
            PhiInstr phi = phis.get(i);
            mips.addInst("lw $t0, 0($sp)");
            mips.addInst("addiu $sp, $sp, 4");
            saveReg(phi, "$t0");
        }
    }
}