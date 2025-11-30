package midend.SSA; // 或者 midend.Optimization

import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;

import java.util.*;

public class DeadCodeElimination {

    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty()) {
                runOnFunction(func);
            }
        }
    }

    private void runOnFunction(IrFunction func) {
        Set<Instruction> liveInstructions = new HashSet<>();
        Queue<Instruction> workList = new LinkedList<>();

        // Step 1: 初始化 - 找出所有明显是关键的指令
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (isCritical(instr)) {
                    liveInstructions.add(instr);
                    workList.add(instr);
                }
            }
        }

        // Step 2: 传播 - 标记活跃指令的操作数来源
        while (!workList.isEmpty()) {
            Instruction current = workList.poll();

            // 获取该指令使用的所有操作数 (IrValue)
            List<IrValue> operands = getOperands(current);

            for (IrValue op : operands) {
                // 我们只关心操作数是由指令产生的情况 (忽略常量和全局变量)
                if (op instanceof Instruction) {
                    Instruction opInstr = (Instruction) op;
                    // 如果这个定义指令还没被标记为活，标记它并加入工作表
                    if (!liveInstructions.contains(opInstr)) {
                        liveInstructions.add(opInstr);
                        workList.add(opInstr);
                    }
                }
            }
        }

        // Step 3: 清除 - 删除死指令
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            // 使用迭代器安全删除
            Iterator<Instruction> it = bb.getInstructions().iterator();
            while (it.hasNext()) {
                Instruction instr = it.next();
                if (!liveInstructions.contains(instr)) {
                    // 从指令列表中移除
                    it.remove();
                    // 这里不需要像 Mem2Reg 那样维护 Use-Def 链的断开，
                    // 因为我们是基于“有用性”反向标记的，没用的直接扔掉即可。
                }
            }
        }
    }

    /**
     * 判断指令是否是“关键指令” (副作用或控制流)
     */
    private boolean isCritical(Instruction instr) {
        // 1. 改变内存状态的
        if (instr instanceof StoreInstr) return true;
        // 2. 函数调用 (可能有副作用，保守起见视为关键)
        if (instr instanceof CallInstr) return true;
        // 3. IO 操作
        if (instr instanceof PrintIntInstr || instr instanceof PrintStrInstr) return true;
        // 4. 控制流 / 终止指令 (不能删，否则 CFG 会断)
        if (instr instanceof ReturnInstr || instr instanceof BranchInstr || instr instanceof JumpInstr) return true;

        // 其他像 Add, Sub, Icmp, Load, Phi, GEP, Zext 等，如果没有人通过上述指令使用它们，就是死代码
        return false;
    }

    /**
     * 辅助方法：获取一条指令用到的所有操作数
     *
     */
    private List<IrValue> getOperands(Instruction instr) {
        List<IrValue> ops = new ArrayList<>();

        if (instr instanceof AluInst) {
            ops.add(((AluInst) instr).getLeft());
            ops.add(((AluInst) instr).getRight());
        } else if (instr instanceof CmpInstr) {
            ops.add(((CmpInstr) instr).getLeft());
            ops.add(((CmpInstr) instr).getRight());
        } else if (instr instanceof LoadInstr) {
            ops.add(((LoadInstr) instr).getPtr());
        } else if (instr instanceof StoreInstr) {
            ops.add(((StoreInstr) instr).getVal());
            ops.add(((StoreInstr) instr).getPtr());
        } else if (instr instanceof BranchInstr) {
            BranchInstr br = (BranchInstr) instr;
            if (br.getCond() != null) {
                ops.add(br.getCond());
            }
        } else if (instr instanceof ReturnInstr) {
            ReturnInstr ret = (ReturnInstr) instr;
            if (!ret.isReturnVoid()) {
                ops.add(ret.getReturnValue());
            }
        } else if (instr instanceof CallInstr) {
            ops.addAll(((CallInstr) instr).getParameters());
        } else if (instr instanceof GepInstr) {
            ops.add(((GepInstr) instr).getPtr());
            ops.add(((GepInstr) instr).getIndice()); // 假设是一维数组索引
        } else if (instr instanceof ZextInstr) {
            ops.add(((ZextInstr) instr).getOperand(0)); // 假设有 getOperand 方法或 public 字段
        } else if (instr instanceof TruncInstr) {
            ops.add(((TruncInstr) instr).getOperand(0));
        } else if (instr instanceof PhiInstr) {
            ops.addAll(((PhiInstr) instr).getIncomingValues());
        } else if (instr instanceof PrintIntInstr) {
            ops.add(((PrintIntInstr) instr).getPrintValue());
        }

        // 注意：JumpInstr, AllocateInstr, PrintStrInstr(操作数是Const) 不需要添加操作数

        return ops;
    }
}