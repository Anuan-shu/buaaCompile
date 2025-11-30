package backend;

import midend.Analysis.LivenessAnalysis;
import midend.LLVM.Instruction.*;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.*;

public class RegisterAllocator {
    public static final String[] REG_NAMES = {
            "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7"
    };
    private static final int MAX_REGISTERS = 8;

    private Map<IrBasicBlock, Set<IrValue>> liveIn = new HashMap<>();
    private Map<IrBasicBlock, Set<IrValue>> liveOut = new HashMap<>();
    private Map<IrBasicBlock, Set<IrValue>> use = new HashMap<>();
    private Map<IrBasicBlock, Set<IrValue>> def = new HashMap<>();
    private Map<IrValue, Set<IrValue>> interferenceGraph = new HashMap<>();
    private Map<IrValue, Integer> allocation = new HashMap<>();

    public Map<IrValue, Integer> run(IrFunction function) {
        allocation.clear();
        buildInterferenceGraph(function, new LivenessAnalysis());
        colorGraph();
        return allocation;
    }

    private void buildInterferenceGraph(IrFunction function, LivenessAnalysis liveness) {
        liveness.run(function);
        // 初始化节点
        for (IrValue arg : function.getParameters()) {
            interferenceGraph.put(arg, new HashSet<>());
        }
        for (IrBasicBlock bb : function.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (!instr.irType.isVoid()) {
                    interferenceGraph.put(instr, new HashSet<>());
                }
            }
        }

        // 3. 构建冲突图
        for (IrBasicBlock bb : function.getBasicBlocks()) {
            Set<IrValue> liveNow = new HashSet<>(liveness.liveOut.get(bb));
            ArrayList<Instruction> insts = bb.getInstructions();

            // 反向遍历指令
            for (int i = insts.size() - 1; i >= 0; i--) {
                Instruction instr = insts.get(i);

                // 1. 处理定义 (Def) - 建立冲突边
                if (!instr.irType.isVoid()) {
                    // 当前指令定义的变量与所有当前活跃的变量冲突
                    for (IrValue live : liveNow) {
                        if (live != instr) { // 自己不冲突
                            addEdge(instr, live);
                        }
                    }
                    // 定义点上方，该变量不再活跃（除非它是 LiveIn，但在 SSA 中 Def 支配 Use，通常直接移除即可）
                    // 对于 Phi，它在块头定义，所以在 Phi 之前的瞬间（即块入口）它是不活跃的
                    liveNow.remove(instr);
                }

                // 2. 处理使用 (Use) - 加入活跃集
                // Phi 指令的操作数不在这里加入活跃集
                // Phi 的操作数是在前驱块的末尾活跃的，而不是当前块。
                if (!(instr instanceof PhiInstr)) {
                    for (IrValue operand : getOperands(instr)) {
                        if (operand instanceof Instruction || operand instanceof midend.LLVM.value.IrParameter) {
                            liveNow.add(operand);
                        }
                    }
                }
            }

            // 处理 Entry Block 的参数冲突
            // 遍历结束后，liveNow 中剩余的就是 LiveIn 的变量。
            // 对于 Entry Block，LiveIn 包含所有被使用的参数。
            // 它们在函数入口同时存活，必须互相冲突，防止分配到同一个寄存器。
            if (bb == function.getEntryBlock()) {
                List<IrValue> liveParams = new ArrayList<>(liveNow);
                for (int i = 0; i < liveParams.size(); i++) {
                    for (int j = i + 1; j < liveParams.size(); j++) {
                        addEdge(liveParams.get(i), liveParams.get(j));
                    }
                }
            }
        }
    }

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

    private void addEdge(IrValue a, IrValue b) {
        if (a.equals(b)) return;
        interferenceGraph.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        interferenceGraph.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    private void colorGraph() {
        Stack<IrValue> stack = new Stack<>();
        Set<IrValue> nodes = new HashSet<>(interferenceGraph.keySet());

        // 简化图
        Map<IrValue, Set<IrValue>> currentGraph = new HashMap<>();
        for (IrValue v : nodes) currentGraph.put(v, new HashSet<>(interferenceGraph.get(v)));

        while (!nodes.isEmpty()) {
            IrValue nodeToRemove = null;
            // 优先移除度数 < K 的节点
            for (IrValue node : nodes) {
                if (currentGraph.get(node).size() < MAX_REGISTERS) {
                    nodeToRemove = node;
                    break;
                }
            }
            // 否则 spill 度数最大的
            if (nodeToRemove == null) {
                int maxDegree = -1;
                for (IrValue node : nodes) {
                    if (currentGraph.get(node).size() > maxDegree) {
                        maxDegree = currentGraph.get(node).size();
                        nodeToRemove = node;
                    }
                }
            }
            nodes.remove(nodeToRemove);
            stack.push(nodeToRemove);
            for (IrValue neighbor : currentGraph.get(nodeToRemove)) {
                if (nodes.contains(neighbor)) currentGraph.get(neighbor).remove(nodeToRemove);
            }
        }

        // 着色
        while (!stack.isEmpty()) {
            IrValue node = stack.pop();
            Set<Integer> usedColors = new HashSet<>();
            if (interferenceGraph.containsKey(node)) {
                for (IrValue neighbor : interferenceGraph.get(node)) {
                    if (allocation.containsKey(neighbor)) {
                        usedColors.add(allocation.get(neighbor));
                    }
                }
            }
            for (int color = 0; color < MAX_REGISTERS; color++) {
                if (!usedColors.contains(color)) {
                    allocation.put(node, color);
                    break;
                }
            }
        }
    }
}