package midend.SSA;

import midend.LLVM.Instruction.BranchInstr;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.JumpInstr;
import midend.LLVM.Instruction.ReturnInstr;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;

public class CfgBuilder {

    /**
     * 构建函数的控制流图 (CFG)
     */
    public static void build(IrFunction function) {
        // 1. 清除旧的 CFG 关系
        for (IrBasicBlock bb : function.getBasicBlocks()) {
            bb.cleanSuccessors();
        }

        // 2. 遍历所有基本块，建立连接
        for (IrBasicBlock currentBlock : function.getBasicBlocks()) {
            Instruction terminator = currentBlock.getTerminator();

            if (terminator == null) {
                continue;
            }

            // 根据终结指令类型，链接后继块
            if (terminator instanceof JumpInstr) {
                // 无条件跳转: j label
                JumpInstr jump = (JumpInstr) terminator;
                link(currentBlock, jump.getTargetBlock());

            } else if (terminator instanceof BranchInstr) {
                // 条件跳转: br cond, trueBB, falseBB
                BranchInstr br = (BranchInstr) terminator;
                link(currentBlock, br.getTrueBlock());
                link(currentBlock, br.getFalseBlock());

            } else if (terminator instanceof ReturnInstr) {
                // 返回指令: 没有后继
                // Do nothing
            }
        }
    }

    /**
     * 建立两个块之间的单向边: src -> dst
     * 同时维护: src 是 dst 的前驱，dst 是 src 的后继
     */
    private static void link(IrBasicBlock src, IrBasicBlock dst) {
        src.addSuccessor(dst);
        dst.addPredecessor(src);
    }
}