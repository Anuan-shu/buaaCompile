package midend.Optimization;

import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.SSA.DominatorTree;

import java.util.*;

public class LoopAnalysis {
    private Map<IrBasicBlock, Integer> loopDepth = new HashMap<>();

    public void run(IrFunction func, DominatorTree domTree) {
        loopDepth.clear();
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            loopDepth.put(bb, 0);
        }

        // 识别回边 (Back Edge): A -> B, 且 B dom A => B 是循环头
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (IrBasicBlock succ : bb.getSuccessors()) {
                if (isDominator(succ, bb, domTree)) {
                    markLoop(bb, succ);
                }
            }
        }
    }

    // 标记循环：从 latch (尾) 回溯到 header (头)
    private void markLoop(IrBasicBlock latch, IrBasicBlock header) {
        if (latch == header) {
            // 自环
            loopDepth.put(header, loopDepth.get(header) + 1);
            return;
        }

        Set<IrBasicBlock> visited = new HashSet<>();
        Queue<IrBasicBlock> q = new LinkedList<>();

        q.offer(latch);
        visited.add(latch);
        visited.add(header); // 只要 header 不入队，就不会穿过它向上溢出

        // 循环头深度 +1
        loopDepth.put(header, loopDepth.get(header) + 1);

        while (!q.isEmpty()) {
            IrBasicBlock curr = q.poll();
            // 路径上的节点深度 +1
            loopDepth.put(curr, loopDepth.get(curr) + 1);

            for (IrBasicBlock pred : curr.getPredecessors()) {
                if (!visited.contains(pred)) {
                    visited.add(pred);
                    q.offer(pred);
                }
            }
        }
    }

    private boolean isDominator(IrBasicBlock a, IrBasicBlock b, DominatorTree domTree) {
        if (a == b) return true;
        IrBasicBlock runner = b;
        while (runner != null) {
            if (runner == a) return true;
            runner = domTree.getIDom(runner);
        }
        return false;
    }

    public int getDepth(IrBasicBlock bb) {
        return loopDepth.getOrDefault(bb, 0);
    }
}