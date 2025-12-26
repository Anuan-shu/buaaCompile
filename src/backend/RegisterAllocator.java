package backend;

import midend.Analysis.LivenessAnalysis;
import midend.LLVM.Instruction.*;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrParameter;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.*;

public class RegisterAllocator {
    public static final String[] REG_NAMES = {
            // Caller-Saved (临时寄存器) - $t0-$t2 保留给 EmitInstruction 使用
            "$t3", "$t4", "$t5", "$t6", "$t7", "$t8", "$t9",
            // Callee-Saved (保存寄存器)
            "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
            // 特殊寄存器
            "$v1", "$gp", "$k0", "$k1",
            // 参数寄存器 - 参数读取后可复用
            "$a0", "$a1", "$a2", "$a3"
    };
    private static final int MAX_REGISTERS = REG_NAMES.length;

    // 定义哪些索引是 Callee-Saved ($s0-$s7)
    // 根据上面的数组，下标 7 到 14 是 $s 寄存器
    public static final int CALLEE_SAVED_START = 7;
    public static final int CALLEE_SAVED_END = 14;

    private Map<IrBasicBlock, Set<IrValue>> liveIn = new HashMap<>();
    private Map<IrBasicBlock, Set<IrValue>> liveOut = new HashMap<>();
    private Map<IrBasicBlock, Set<IrValue>> use = new HashMap<>();
    private Map<IrBasicBlock, Set<IrValue>> def = new HashMap<>();
    private Map<IrValue, Set<IrValue>> interferenceGraph = new HashMap<>();
    private Map<IrValue, Integer> allocation = new HashMap<>();

    // 记录哪些变量跨越了函数调用 (Call Instruction)
    private Set<IrValue> valuesCrossingCalls = new HashSet<>();

    // 记录每个变量的使用次数（用于 spill 决策）
    private Map<IrValue, Integer> useCount = new HashMap<>();

    public Map<IrValue, Integer> run(IrFunction function) {
        allocation.clear();
        valuesCrossingCalls.clear();
        interferenceGraph.clear();
        useCount.clear();

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

                // 检测跨越 Call 的变量 ---
                if (instr instanceof CallInstr) {
                    // 在 Call 指令处仍然活跃的变量，说明它们跨越了函数调用
                    // 这些变量必须分配到 $s 寄存器，或者是 Spill 到栈上
                    valuesCrossingCalls.addAll(liveNow);
                }

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

                // 2. 处理使用 (Use) - 加入活跃集并计数
                // Phi 指令的操作数不在这里加入活跃集
                // Phi 的操作数是在前驱块的末尾活跃的，而不是当前块。
                if (!(instr instanceof PhiInstr)) {
                    for (IrValue operand : getOperands(instr)) {
                        if (operand instanceof Instruction || operand instanceof IrParameter) {
                            liveNow.add(operand);
                            // 统计使用次数
                            useCount.merge(operand, 1, Integer::sum);
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
            // 使用 spill cost 启发式：选择 useCount / degree 最小的节点
            // 这样会优先 spill 使用次数少、邻居多的变量
            if (nodeToRemove == null) {
                double minCost = Double.MAX_VALUE;
                for (IrValue node : nodes) {
                    int degree = currentGraph.get(node).size();
                    int uses = useCount.getOrDefault(node, 1);
                    double cost = (double) uses / Math.max(degree, 1);
                    if (cost < minCost) {
                        minCost = cost;
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

            // 根据变量是否跨越 Call 来限制颜色选择 ---
            boolean isCrossCall = valuesCrossingCalls.contains(node);

            int colorFound = -1;
            for (int color = 0; color < MAX_REGISTERS; color++) {
                // 如果已经被邻居占用，跳过
                if (usedColors.contains(color)) continue;

                // 如果变量跨越 Call，它必须使用 Callee-Saved ($s0-$s7)
                // 对应下标 CALLEE_SAVED_START 到 CALLEE_SAVED_END
                if (isCrossCall) {
                    if (color < CALLEE_SAVED_START || color > CALLEE_SAVED_END) {
                        continue; // 跳过非 $s 寄存器
                    }
                }

                colorFound = color;
                break;
            }

            if (colorFound != -1) {
                allocation.put(node, colorFound);
            } else {
                // Spill 发生
            }
        }
    }
}