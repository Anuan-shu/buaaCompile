package backend;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Const.IrConstIntArray;
import midend.LLVM.Const.IrConstString;
import midend.LLVM.Const.IrConstant;
import midend.LLVM.Instruction.AllocateInstruction;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.IrModule;
import midend.LLVM.value.*;
import midend.SSA.PhiInstr;

import java.util.*;

public class MipsBuilder {
    private static MipsModule mips = new MipsModule();
    private static HashMap<IrValue, Integer> offsetMap = new HashMap<>();
    private static int currentFunctionStackSize = 0;
    private static HashMap<AllocateInstruction, Integer> allocaArrayOffsets = new HashMap<>();

    private static RegisterAllocator registerAllocator = new RegisterAllocator();

    // 给一个 Value 分配栈空间
    private static void allocateStack(IrValue value, int sizeBytes) {
        // 栈向下增长，所以偏移量是负数
        // currentFunctionStackSize 记录当前已用的字节数（正数）
        currentFunctionStackSize += sizeBytes;
        offsetMap.put(value, -currentFunctionStackSize);
    }

    public static MipsModule getMipsModule() {
        return mips;
    }

    public static MipsModule generate(IrModule irModule, boolean optimize) {
        mips = new MipsModule(); // 重置 Module 防止多次调用叠加
        // 1. 处理字符串常量
        for (IrConstString irConstString : irModule.getStringIrConstStringHashMap().values()) {
            emitStringConst(irConstString);
        }

        // 2. 处理全局变量
        for (IrGlobalValue global : irModule.getGlobals()) {
            emitGlobalVar(global);
        }

        mips.addInst("j main");
        mips.addInst("nop");

        // 3. 处理函数
        for (IrFunction function : irModule.getFunctions()) {
            emitFunction(function, optimize);
        }

        // 4. 库函数
        appendLibraryFunctions();

        return mips;
    }

    private static void appendLibraryFunctions() {
        // getint: 读取一个整数到 $v0
        mips.addInst("getint:");
        mips.addInst("li $v0, 5");   // Syscall 5: read_int
        mips.addInst("syscall");
        mips.addInst("jr $ra");      // 返回调用者 ($v0 中是读取的值)
        mips.addInst("nop");
    }

    private static void emitFunction(IrFunction function, boolean optimize) {
        // 0. 初始化状态
        offsetMap.clear();
        currentFunctionStackSize = 0; // 重置栈计数
        allocaArrayOffsets.clear(); // 重置 alloca 数组偏移记录


        Map<IrValue, Integer> regAllocation = null;
        Set<Integer> usedSRegs = new HashSet<>();

        if (optimize) {
            regAllocation = registerAllocator.run(function);
            usedSRegs.addAll(regAllocation.values());
        } else {
            regAllocation = new HashMap<>(); // 空 map
        }
        // Step 1: 预计算栈空间

        // 1.1 为保存寄存器预留空间 ($ra, $fp)
        // $ra @ -4($fp), $fp @ -8($fp)
        currentFunctionStackSize += 8;
        currentFunctionStackSize += (usedSRegs.size() * 4);
        // 1.2 为参数分配空间
        for (IrValue arg : function.getParameters()) {
            allocateStack(arg, 4); // 每个参数分配 4 字节
        }

        // 1.3 为函数体内所有有返回值的指令分配空间
        for (IrBasicBlock bb : function.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                // 如果是 Alloca 指令，需要特殊处理：
                // 1. 分配指针变量本身的空间 (4字节)
                // 2. 分配数组实体的空间 (N字节)

                // 如果是 alloca 数组，还需要额外在栈上挖一块空地
                // 比如: %arr = alloca [10 x i32]
                if (instr instanceof AllocateInstruction) {
                    // 1. 为指针变量 分配 4 字节
                    allocateStack(instr, 4);

                    // 2. 为数组实体分配 N 字节
                    int size = ((AllocateInstruction) instr).getAllocatedSize();
                    if (size % 4 != 0) {
                        size += (4 - (size % 4));
                    }
                    currentFunctionStackSize += size;

                    // 3. 记录数组实体相对于 $fp 的偏移量
                    // 实体位于当前栈底 (也就是 -currentFunctionStackSize)
                    allocaArrayOffsets.put((AllocateInstruction) instr, -currentFunctionStackSize);
                } else if (instr instanceof PhiInstr) {
                    //System.out.println("DEBUG: Allocating stack for Phi: " + instr.irName);
                    allocateStack(instr, 4);
                } else if (!instr.irType.isVoid()) {
                    allocateStack(instr, 4);
                }
            }
        }

        if (currentFunctionStackSize % 8 != 0) {
            currentFunctionStackSize += (8 - (currentFunctionStackSize % 8));
        }

        // Step 2: 生成函数序言

        // 2.1 输出函数标签 (去掉 @)
        String label = function.irName.substring(1);
        label = label.replace("@", "").replace(".", "_");
        mips.addInst("\n" + label + ":");

        // 先建立栈帧指针，再开栈，最后保存寄存器

        // 1. 保存旧的 $fp  到当前栈顶下方的预留位
        mips.addInst("sw $fp, -8($sp)");

        // 2. 设置新的 $fp (指向当前栈帧的基址，即 Old SP)
        mips.addInst("move $fp, $sp");

        // 3. 分配栈空间 (更新 $sp)
        if (currentFunctionStackSize > 32767) {
            // 如果栈太大，不能用立即数，使用 $t0 中转
            mips.addInst("li $t0, -" + currentFunctionStackSize);
            mips.addInst("addu $sp, $sp, $t0");
        } else {
            // 栈较小，直接用 addiu
            mips.addInst("addiu $sp, $sp, -" + currentFunctionStackSize);
        }

        // 4. 保存 $ra
        // 注意：此时已分配空间，使用 $fp 寻址 (因为 $fp == Old SP)
        mips.addInst("sw $ra, -4($fp)");

        // 5. 保存 Callee-Saved 寄存器 ($sX)
        int extraSaveSize = 0;
        // 建立一个 map 传给 EmitInstruction，用于函数返回时恢复寄存器
        Map<Integer, Integer> sRegStackOffsets = new HashMap<>();

        if (optimize) {
            // 遍历 RegisterAllocator 定义的正确常量范围
            for (int i = RegisterAllocator.CALLEE_SAVED_START; i <= RegisterAllocator.CALLEE_SAVED_END; i++) {
                if (usedSRegs.contains(i)) {
                    String sReg = RegisterAllocator.REG_NAMES[i]; // 获取 "$sX"

                    extraSaveSize += 4;
                    int currentOffset = -(8 + extraSaveSize); // 计算偏移: -12, -16 ...

                    // 使用 $fp 进行寻址
                    // $sp 已经减过了，必须用 $fp
                    mips.addInst(String.format("sw %s, %d($fp)", sReg, currentOffset));

                    // 记录偏移量，供 EmitInstruction 的 emitRet 使用
                    sRegStackOffsets.put(i, currentOffset);
                }
            }
        }

        // Step 3: 保存参数
        // MIPS 约定前 4 个参数在 $a0-$a3，我们需要把它们存入刚才分配的栈位置中
        // 这样后续指令就可以像读普通变量一样读参数了
        ArrayList<IrParameter> args = function.getParameters();
        for (int i = 0; i < args.size(); i++) {
            IrValue arg = args.get(i);
            int offset = offsetMap.get(arg);

            // 检查参数是否被分配到了寄存器
            Integer allocatedReg = regAllocation.get(arg);

            if (i < 4) {
                if (allocatedReg != null) {
                    // 优化：直接移入分配的寄存器 $sX
                    mips.addInst("move " + RegisterAllocator.REG_NAMES[allocatedReg] + ", $a" + i);
                } else {
                    // 未分配寄存器：存入栈
                    saveToStackOrReg("$a" + i, offset, true);
                }
            } else {
                // 1. 计算该参数在 Caller 栈帧中的位置
                // 第5个参数在 0($sp)，即 0($fp)
                // 第6个参数在 4($fp)...
                int callerOffset = (i - 4) * 4;

                // 2. 从 Caller 栈帧加载参数到临时寄存器 $t0
                // 注意：这里是正偏移，访问的是 Caller 放置参数的区域
                mips.addInst("lw $t0, " + callerOffset + "($fp)");

                // 3. 将参数保存到当前函数的局部栈帧
                // offset 是 Step 1 分配的负偏移量
                // 后续指令访问 %arg 时，统一去 offset($fp) 读取
                if (allocatedReg != null) {
                    mips.addInst("move " + RegisterAllocator.REG_NAMES[allocatedReg] + ", $t0");
                } else {
                    saveToStackOrReg("$t0", offset, true);
                }
            }
        }

        // Step 4: 遍历基本块 (Basic Blocks)
        String funcLabel = function.irName.substring(1);

        EmitInstruction emitter = new EmitInstruction(mips, offsetMap, allocaArrayOffsets, funcLabel, regAllocation, sRegStackOffsets);

        for (IrBasicBlock bb : function.getBasicBlocks()) {
            String bbLabel = bb.irName.replace("@", "").replace(".", "_");
            // 生成块标签 (block_name:)
            mips.addInst(label + "_" + bbLabel + ":");

            for (Instruction instr : bb.getInstructions()) {
                // 调用指令翻译器
                emitter.emit(instr, optimize);
            }
        }
    }

    // 辅助方法：处理大偏移量的 sw
    private static void saveToStackOrReg(String srcReg, int offset, boolean useFp) {
        if (offset >= -32768 && offset <= 32767) {
            mips.addInst("sw " + srcReg + ", " + offset + "($fp)");
        } else {
            mips.addInst("li $at, " + offset);
            mips.addInst("addu $at, $fp, $at");
            mips.addInst("sw " + srcReg + ", 0($at)");
        }
    }

    private static void emitGlobalVar(IrGlobalValue global) {
        // 1. 获取标签名：去掉 @
        // @a -> a
        String label = global.irName.substring(1);

        // 2. 获取初始值
        IrValue initVal = global.getInitial();
        mips.addGlobal(".align 2");
        // ----------------------------------------
        // 情况 A: 单个整数 (i32)
        // @a = dso_local global i32 10
        // ----------------------------------------
        if (initVal instanceof IrConstInt) {
            int val = ((IrConstInt) initVal).getValue();
            mips.addGlobal(String.format("%s: .word %d", label, val));
        }

        // ----------------------------------------
        // 情况 B: 数组 ([n x i32])
        // @arr = dso_local global [5 x i32] [i32 1, i32 2, ...]
        // 或
        // @arr = dso_local global [100 x i32] zeroinitializer
        // ----------------------------------------
        else if (initVal instanceof IrConstIntArray irArray) {
            ArrayList<IrConstant> elements = irArray.getArray();

            // 获取数组声明的总长度 (注意：IrConstIntArray 中 irType.arraySize 是总长度)
            int totalSize = irArray.irType.arraySize;

            // A. 如果数组全是 0 (null 或 显式空) -> 使用 .space 优化
            if (elements == null) {
                mips.addGlobal(String.format("%s: .space %d", label, totalSize * 4));
                return;
            }

            // B. 有具体数值，生成 .word
            mips.addGlobal(label + ":"); // 先单独输出标签

            StringBuilder sb = new StringBuilder();
            int count = 0; // 计数器

            // 1. 输出已有元素
            for (int i = 0; i < elements.size(); i++) {
                if (count == 0) sb.append(".word "); // 每行开头加 .word

                IrConstInt constInt = (IrConstInt) elements.get(i);
                sb.append(constInt.getValue());
                count++;

                // 每 50 个元素，或者到了最后一个元素，就换行输出
                if (count == 50 || (i == elements.size() - 1 && elements.size() == totalSize)) {
                    mips.addGlobal(sb.toString());
                    sb = new StringBuilder(); // 清空 buffer
                    count = 0;
                } else {
                    sb.append(", ");
                }
            }

            // 2. 处理补零逻辑
            for (int i = elements.size(); i < totalSize; i++) {
                if (count == 0) sb.append(".word ");

                sb.append("0");
                count++;

                if (count == 50 || i == totalSize - 1) {
                    mips.addGlobal(sb.toString());
                    sb = new StringBuilder();
                    count = 0;
                } else {
                    sb.append(", ");
                }
            }
        }
    }

    private static void emitStringConst(IrConstString irConstString) {
        // @s_0 -> s_0
        String label = irConstString.irName.substring(1);

        // 1. 取 c" 和最后一个 " 之间的内容
        String rawFull = irConstString.toString();
        int start = rawFull.indexOf("c\"") + 2;
        int end = rawFull.lastIndexOf("\"");

        String content = rawFull.substring(start, end);

        // 2. 将 LLVM IR 的 Hex 转义符 替换为 MIPS 的转义符
        // 先处理特定的 Hex 码，最后再处理普通字符

        // 换行符: \0A -> \n
        content = content.replace("\\0A", "\\n");
        // 空字符: \00 -> 空字符串 (.asciiz 会自动补0，所以这里删掉)
        content = content.replace("\\00", "");
        // 双引号: \22 -> \"
        content = content.replace("\\22", "\\\"");
        // 反斜杠: \5C -> \\
        content = content.replace("\\5C", "\\\\");

        // 3. 拼接汇编指令，给 content 加上双引号
        String asm = String.format("%s: .asciiz \"%s\"", label, content);

        mips.addGlobal(asm);
    }

}
