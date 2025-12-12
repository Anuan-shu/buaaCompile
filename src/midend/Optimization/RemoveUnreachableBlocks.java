package midend.Optimization;

import midend.LLVM.Instruction.BranchInstr;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.JumpInstr;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;

import java.util.*;

public class RemoveUnreachableBlocks {
    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty()) {
                runOnFunction(func);
            }
        }
    }

    private void runOnFunction(IrFunction func) {
        // 1. 广度优先搜索 (BFS) 标记所有可达块
        Set<IrBasicBlock> reachable = new HashSet<>();
        Queue<IrBasicBlock> q = new LinkedList<>();

        IrBasicBlock entry = func.getEntryBlock();
        if (entry != null) {
            reachable.add(entry);
            q.add(entry);
        }

        while (!q.isEmpty()) {
            IrBasicBlock bb = q.poll();
            // 获取后继块
            for (IrBasicBlock succ : getSuccessors(bb)) {
                if (!reachable.contains(succ)) {
                    reachable.add(succ);
                    q.add(succ);
                }
            }
        }

        // 2. 移除不可达块
        ArrayList<IrBasicBlock> blocks = func.getBasicBlocks();
        // 使用倒序遍历以安全删除
        for (int i = blocks.size() - 1; i >= 0; i--) {
            IrBasicBlock bb = blocks.get(i);
            if (!reachable.contains(bb)) {
                // 从所有后继块的前驱列表中移除自己
                // 这一步至关重要：它会更新后继块中的 Phi 节点，移除对应分支
                for (IrBasicBlock succ : getSuccessors(bb)) {
                    succ.removePredecessor(bb);
                }

                // 物理删除该块
                blocks.remove(i);
            }
        }
    }

    private ArrayList<IrBasicBlock> getSuccessors(IrBasicBlock bb) {
        ArrayList<IrBasicBlock> succs = new ArrayList<>();
        Instruction term = bb.getTerminator();
        if (term instanceof JumpInstr) {
            succs.add(((JumpInstr) term).getTargetBlock());
        } else if (term instanceof BranchInstr) {
            succs.add(((BranchInstr) term).getTrueBlock());
            succs.add(((BranchInstr) term).getFalseBlock());
        }
        return succs;
    }
}