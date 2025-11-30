package midend.Optimization;

import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.DominatorTree;
import midend.SSA.PhiInstr;

import java.util.*;

public class GlobalCodeMotion {
    private DominatorTree domTree;
    private LoopAnalysis loopAnalysis;

    // Use-Def 链: Value -> List<User>
    private Map<IrValue, List<Instruction>> useMap = new HashMap<>();

    // Early Schedule 结果
    private Map<Instruction, IrBasicBlock> scheduleEarly = new HashMap<>();

    // 访问标记
    private Set<Instruction> visited = new HashSet<>();

    // 最终移动的目标: Block -> List<Instr>
    private Map<IrBasicBlock, List<Instruction>> newLocations = new HashMap<>();

    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            if (func.getBasicBlocks().isEmpty()) continue;
            runOnFunction(func);
        }
    }

    private void runOnFunction(IrFunction func) {
        // 1. 初始化分析
        domTree = new DominatorTree(func);
        loopAnalysis = new LoopAnalysis();
        loopAnalysis.run(func, domTree);

        buildUseDefChains(func);

        visited.clear();
        scheduleEarly.clear();
        newLocations.clear();

        // 2. Schedule Early (下沉到数据依赖允许的最早位置)
        // 遍历所有指令进行递归
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (!visited.contains(instr)) {
                    scheduleEarly(instr, func.getEntryBlock());
                }
            }
        }

        // 3. Schedule Late (上浮到 Use 允许的最晚位置，并选择最佳循环层级)
        visited.clear();
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (!visited.contains(instr)) {
                    scheduleLate(instr);
                }
            }
        }

        // 4. 移动代码并局部排序
        moveInstructions(func);
    }

    private void buildUseDefChains(IrFunction func) {
        useMap.clear();
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                for (IrValue op : getOperands(instr)) {
                    useMap.computeIfAbsent(op, k -> new ArrayList<>()).add(instr);
                }
            }
        }
    }

    // 获取操作数
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
            if (br.getCond() != null) ops.add(br.getCond());
        } else if (instr instanceof ReturnInstr) {
            ReturnInstr ret = (ReturnInstr) instr;
            if (!ret.isReturnVoid()) ops.add(ret.getReturnValue());
        } else if (instr instanceof CallInstr) {
            ops.addAll(((CallInstr) instr).getParameters());
        } else if (instr instanceof GepInstr) {
            ops.add(((GepInstr) instr).getPtr());
            ops.add(((GepInstr) instr).getIndice());
        } else if (instr instanceof ZextInstr) {
            ops.add(((ZextInstr) instr).getOperand(0));
        } else if (instr instanceof TruncInstr) {
            ops.add(((TruncInstr) instr).getOperand(0));
        } else if (instr instanceof PhiInstr) {
            ops.addAll(((PhiInstr) instr).getIncomingValues());
        } else if (instr instanceof PrintIntInstr) {
            ops.add(((PrintIntInstr) instr).getPrintValue());
        } else if (instr instanceof PrintStrInstr) {
            ops.add(((PrintStrInstr) instr).getPrintValue());
        }
        return ops;
    }

    // Step 2: Schedule Early
    private IrBasicBlock scheduleEarly(Instruction instr, IrBasicBlock entry) {
        if (visited.contains(instr)) return scheduleEarly.get(instr);
        visited.add(instr);

        // Pinned 指令固定在原块
        if (instr.isPinned()) {
            IrBasicBlock pinnedBlock = (IrBasicBlock) instr.getParent();
            scheduleEarly.put(instr, pinnedBlock);
            return pinnedBlock;
        }

        IrBasicBlock earlyBlock = entry;

        for (IrValue op : getOperands(instr)) {
            if (op instanceof Instruction) {
                Instruction opInstr = (Instruction) op;
                IrBasicBlock opBlock = scheduleEarly(opInstr, entry);

                // 如果操作数是 Phi，OpBlock 就是循环头
                // 无论 DomDepth 如何计算，EarlyBlock 绝不能比 OpBlock "浅"
                // 在支配树上，Deep > Shallow。

                // 如果 domTree 出错导致 opBlock 深度看起来很小，直接使用 opBlock
                if (domTree.getDomDepth(opBlock) > domTree.getDomDepth(earlyBlock)) {
                    earlyBlock = opBlock;
                }
            }
        }

        scheduleEarly.put(instr, earlyBlock);
        return earlyBlock;
    }

    // --- Schedule Late ---
    private void scheduleLate(Instruction instr) {
        if (visited.contains(instr)) return;
        visited.add(instr);

        if (instr.isPinned()) return;

        IrBasicBlock lca = null;
        List<Instruction> users = useMap.getOrDefault(instr, Collections.emptyList());

        for (Instruction user : users) {
            scheduleLate(user);

            IrBasicBlock userBlock;
            // Phi 指令的 User Block 是 Incoming Block
            if (user instanceof PhiInstr) {
                userBlock = getPhiUserBlock((PhiInstr) user, instr);
            } else {
                userBlock = (IrBasicBlock) user.getParent();
            }

            // LCA 计算
            lca = (lca == null) ? userBlock : findLCA(lca, userBlock);
        }

        // 如果没有 User (死代码)，暂留本块
        if (lca == null) lca = (IrBasicBlock) instr.getParent();

        // 选择最佳位置：在 Early 和 Late (lca) 之间寻找循环深度最小的块
        IrBasicBlock bestBlock = lca;
        IrBasicBlock curr = lca;
        IrBasicBlock early = scheduleEarly.get(instr);

        while (curr != early && curr != null) {
            if (loopAnalysis.getDepth(curr) < loopAnalysis.getDepth(bestBlock)) {
                bestBlock = curr;
            }
            curr = domTree.getIDom(curr);
        }
        // 检查 Early 块本身
        if (curr != null && loopAnalysis.getDepth(curr) < loopAnalysis.getDepth(bestBlock)) {
            bestBlock = curr;
        }

        // 记录到新位置
        newLocations.computeIfAbsent(bestBlock, k -> new ArrayList<>()).add(instr);

        // 更新指令的 Parent 引用 (为了后续 scheduleLate 递归调用获取正确 parent)
        instr.setParentBasicBlock(bestBlock);
    }

    private IrBasicBlock getPhiUserBlock(PhiInstr phi, IrValue val) {
        ArrayList<IrValue> vals = phi.getIncomingValues();
        ArrayList<IrBasicBlock> blks = phi.getIncomingBlocks();
        // 必须遍历所有匹配的输入，计算共同的 LCA
        // 因为一个值可能从多个前驱传入同一个 Phi
        IrBasicBlock phiLca = null;

        for (int i = 0; i < vals.size(); i++) {
            if (vals.get(i) == val) {
                IrBasicBlock blk = blks.get(i);
                if (phiLca == null) phiLca = blk;
                else phiLca = findLCA(phiLca, blk);
            }
        }
        return phiLca;
    }

    private IrBasicBlock findLCA(IrBasicBlock a, IrBasicBlock b) {
        if (a == null) return b;
        if (b == null) return a;
        while (domTree.getDomDepth(a) > domTree.getDomDepth(b)) a = domTree.getIDom(a);
        while (domTree.getDomDepth(b) > domTree.getDomDepth(a)) b = domTree.getIDom(b);
        while (a != b) {
            a = domTree.getIDom(a);
            b = domTree.getIDom(b);
        }
        return a;
    }

    // --- Move Instructions (Local Topological Sort) ---
    private void moveInstructions(IrFunction func) {
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            List<Instruction> instructions = bb.getInstructions();

            List<Instruction> phis = new ArrayList<>();
            List<Instruction> pinned = new ArrayList<>();

            // 1. 收集指令
            Iterator<Instruction> it = instructions.iterator();
            while (it.hasNext()) {
                Instruction instr = it.next();
                if (instr instanceof PhiInstr) {
                    phis.add(instr);
                } else if (instr.isPinned()) {
                    pinned.add(instr);
                }
                it.remove(); // 清空，稍后重建
            }

            // 2. 提取 Terminator (必须在最后)
            Instruction terminator = null;
            if (!pinned.isEmpty() && pinned.get(pinned.size() - 1).isTerminator()) {
                terminator = pinned.remove(pinned.size() - 1);
            }

            // 3. 混合 Pinned 和 GCM 指令
            List<Instruction> gcmInstrs = newLocations.getOrDefault(bb, new ArrayList<>());
            List<Instruction> allNode = new ArrayList<>();
            allNode.addAll(pinned);
            allNode.addAll(gcmInstrs);

            // 4. 构建依赖图进行拓扑排序
            Map<Instruction, Integer> inDegree = new HashMap<>();
            Map<Instruction, List<Instruction>> graph = new HashMap<>();
            for (Instruction i : allNode) {
                inDegree.put(i, 0);
                graph.put(i, new ArrayList<>());
            }

            // 数据依赖: Op -> User
            for (Instruction instr : allNode) {
                for (IrValue op : getOperands(instr)) {
                    if (op instanceof Instruction && inDegree.containsKey(op)) {
                        Instruction def = (Instruction) op;
                        graph.get(def).add(instr);
                        inDegree.put(instr, inDegree.get(instr) + 1);
                    }
                }
            }

            // 顺序依赖: Pinned[i] -> Pinned[i+1]
            for (int i = 0; i < pinned.size() - 1; i++) {
                Instruction u = pinned.get(i);
                Instruction v = pinned.get(i + 1);
                graph.get(u).add(v);
                inDegree.put(v, inDegree.get(v) + 1);
            }

            // 5. 拓扑排序 (Kahn's Algorithm)
            Queue<Instruction> q = new LinkedList<>();
            for (Instruction i : allNode) {
                if (inDegree.get(i) == 0) q.offer(i);
            }

            List<Instruction> sorted = new ArrayList<>();
            while (!q.isEmpty()) {
                Instruction u = q.poll();
                sorted.add(u);
                for (Instruction v : graph.get(u)) {
                    int d = inDegree.get(v) - 1;
                    inDegree.put(v, d);
                    if (d == 0) q.offer(v);
                }
            }

            // 6. 重建列表
            instructions.addAll(phis);   // Phi 永远在头
            instructions.addAll(sorted); // 排序后的指令
            if (terminator != null) {
                instructions.add(terminator); // 终结指令永远在尾
            }
        }
    }
}