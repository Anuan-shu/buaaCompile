package midend.SSA;

import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;

import java.util.*;

public class DominatorTree {
    private final IrFunction function;

    // 记录每个块的直接支配者
    private final Map<IrBasicBlock, IrBasicBlock> idoms = new HashMap<>();

    // 支配树的子节点列表 (用于遍历支配树)
    private final Map<IrBasicBlock, List<IrBasicBlock>> domTreeChildren = new HashMap<>();

    // 支配边界
    private final Map<IrBasicBlock, Set<IrBasicBlock>> dominanceFrontier = new HashMap<>();

    // 逆后序遍历列表
    private final List<IrBasicBlock> rpoList = new ArrayList<>();

    // 记录每个块在 RPO 中的索引，用于快速比较 (Intersect 算法需要)
    private final Map<IrBasicBlock, Integer> rpoIndex = new HashMap<>();

    public DominatorTree(IrFunction function) {
        this.function = function;
        CfgBuilder.build(function);
        build();
    }

    /**
     * 构建支配树的主流程
     */
    private void build() {
        // 1. 初始化数据结构
        for (IrBasicBlock bb : function.getBasicBlocks()) {
            domTreeChildren.put(bb, new ArrayList<>());
            dominanceFrontier.put(bb, new HashSet<>());
        }

        // 2. 计算逆后序 (RPO)
        // 迭代算法在 RPO 顺序下收敛最快
        calculateRPO(function.getEntryBlock());

        // 3. 计算直接支配者 (IDoms)
        calculateIDoms();

        // 4. 构建支配树的层次结构 (Children)
        buildDomTreeHierarchy();

        // 5. 计算支配边界 (Dominance Frontier)
        calculateDominanceFrontier();

        // 6. 计算支配树深度
        calculateDomDepth();
    }

    /**
     * 计算逆后序 (Reverse Post Order)
     * 使用 DFS 后序遍历，然后反转
     */
    private void calculateRPO(IrBasicBlock entry) {
        Set<IrBasicBlock> visited = new HashSet<>();
        Stack<IrBasicBlock> postOrderStack = new Stack<>();

        dfsPostOrder(entry, visited, postOrderStack);

        int index = 0;
        while (!postOrderStack.isEmpty()) {
            IrBasicBlock bb = postOrderStack.pop();
            rpoList.add(bb);
            rpoIndex.put(bb, index++);
        }
    }

    private void dfsPostOrder(IrBasicBlock curr, Set<IrBasicBlock> visited, Stack<IrBasicBlock> stack) {
        visited.add(curr);
        for (IrBasicBlock succ : curr.getSuccessors()) {
            if (!visited.contains(succ)) {
                dfsPostOrder(succ, visited, stack);
            }
        }
        stack.push(curr);
    }

    /**
     * 使用迭代法计算 IDom
     * 算法来源: Cooper, Harvey, and Kennedy
     */
    void calculateIDoms() {
        IrBasicBlock entry = function.getEntryBlock();

        // 初始化：Entry 的 idom 是它自己 (或者 null，方便起见设为自己，但在循环中要特判)
        idoms.put(entry, entry);

        boolean changed = true;
        while (changed) {
            changed = false;

            // 按 RPO 顺序遍历所有块 (跳过 Entry)
            for (IrBasicBlock bb : rpoList) {
                if (bb == entry) continue;

                // 1. 找第一个已经处理过的 (有 IDom) 的前驱作为候选 IDom
                List<IrBasicBlock> preds = bb.getPredecessors();
                if (preds.isEmpty()) continue; // 死代码块，忽略

                IrBasicBlock newIdom = null;
                for (IrBasicBlock pred : preds) {
                    if (idoms.containsKey(pred)) {
                        newIdom = pred;
                        break;
                    }
                }

                if (newIdom == null) continue; // 应该不会发生，除非不可达

                // 2. 遍历其他前驱，计算最近公共祖先
                for (IrBasicBlock pred : preds) {
                    if (pred != newIdom && idoms.containsKey(pred)) {
                        newIdom = intersect(newIdom, pred);
                    }
                }

                // 3. 如果 IDom 发生变化，更新并标记 changed
                if (!newIdom.equals(idoms.get(bb))) {
                    idoms.put(bb, newIdom);
                    changed = true;
                }
            }
        }

        // 修正：Entry 的 IDom 设为 null，因为它没有严格支配者
        idoms.put(entry, null);
    }

    /**
     * 寻找两个节点在支配树上的最近公共祖先
     * 利用 RPO 索引：索引越大，在支配树中越靠下（深）
     */
    private IrBasicBlock intersect(IrBasicBlock b1, IrBasicBlock b2) {
        IrBasicBlock finger1 = b1;
        IrBasicBlock finger2 = b2;

        while (finger1 != finger2) {
            // 将 RPO 索引较大的指针向上移动
            while (rpoIndex.get(finger1) > rpoIndex.get(finger2)) {
                finger1 = idoms.get(finger1);
            }
            while (rpoIndex.get(finger2) > rpoIndex.get(finger1)) {
                finger2 = idoms.get(finger2);
            }
        }
        return finger1;
    }

    /**
     * 根据 IDom 关系填充 Children 列表
     */
    private void buildDomTreeHierarchy() {
        for (IrBasicBlock bb : rpoList) {
            if (bb == function.getEntryBlock()) continue;

            IrBasicBlock idom = idoms.get(bb);
            if (idom != null) {
                domTreeChildren.get(idom).add(bb);
            }
        }
    }

    /**
     * 计算支配边界
     * 算法：
     * for each block B with >= 2 predecessors:
     * for each predecessor P of B:
     * runner = P
     * while runner != idom(B):
     * add B to runner.DF
     * runner = idom(runner)
     */
    void calculateDominanceFrontier() {
        for (IrBasicBlock bb : rpoList) {
            List<IrBasicBlock> preds = bb.getPredecessors();
            if (preds.size() >= 2) {
                for (IrBasicBlock p : preds) {
                    IrBasicBlock runner = p;
                    // 向上回溯直到碰到 idom(bb)
                    // 注意：这里需要判空，防止死循环 (对于不可达代码)
                    while (runner != idoms.get(bb) && runner != null) {
                        dominanceFrontier.get(runner).add(bb);
                        runner = idoms.get(runner);
                    }
                }
            }
        }
    }

    /**
     * 获取某节点的支配边界
     */
    public Set<IrBasicBlock> getDominanceFrontier(IrBasicBlock bb) {
        return dominanceFrontier.getOrDefault(bb, Collections.emptySet());
    }

    /**
     * 获取某节点在支配树上的直接子节点
     */
    public List<IrBasicBlock> getChildren(IrBasicBlock bb) {
        return domTreeChildren.getOrDefault(bb, Collections.emptyList());
    }

    /**
     * 获取直接支配者
     */
    public IrBasicBlock getIDom(IrBasicBlock bb) {
        return idoms.get(bb);
    }

    private Map<IrBasicBlock, Integer> domDepth = new HashMap<>();

    // 在 build() 最后计算深度
    private void calculateDomDepth() {
        domDepth.clear();
        // 对所有基本块计算深度
        for (IrBasicBlock bb : function.getBasicBlocks()) {
            int depth = 0;
            IrBasicBlock runner = bb;
            // 向上回溯直到根节点或 null
            while (runner != null && idoms.get(runner) != null && runner != idoms.get(runner)) {
                depth++;
                runner = idoms.get(runner);
            }
            domDepth.put(bb, depth);
        }
        // 修正 Entry 深度为 0
        domDepth.put(function.getEntryBlock(), 0);
    }


    public int getDomDepth(IrBasicBlock bb) {
        if (!domDepth.containsKey(bb)) {
            return Integer.MAX_VALUE;
        }
        return domDepth.get(bb);
    }

}