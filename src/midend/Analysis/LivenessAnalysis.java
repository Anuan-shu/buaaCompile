package midend.Analysis;

import midend.LLVM.Instruction.*;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.*;

public class LivenessAnalysis {
    // LiveIn[B]: 进入 Block B 时活跃的变量集合
    public Map<IrBasicBlock, Set<IrValue>> liveIn = new HashMap<>();
    // LiveOut[B]: 离开 Block B 时活跃的变量集合
    public Map<IrBasicBlock, Set<IrValue>> liveOut = new HashMap<>();

    // Use[B]: 在 B 中被引用，且引用前未在 B 中被定义的变量
    private Map<IrBasicBlock, Set<IrValue>> use = new HashMap<>();
    // Def[B]: 在 B 中定义（被赋值）的变量
    private Map<IrBasicBlock, Set<IrValue>> def = new HashMap<>();

    public void run(IrFunction func) {
        liveIn.clear();
        liveOut.clear();
        use.clear();
        def.clear();

        // 1. 初始化并计算每个块局部的 Use 和 Def 集合
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            liveIn.put(bb, new HashSet<>());
            liveOut.put(bb, new HashSet<>());
            calcUseDef(bb);
        }

        // 2. 迭代计算 LiveIn 和 LiveOut (不动点算法)
        boolean changed = true;
        while (changed) {
            changed = false;
            // 反向遍历基本块有助于数据流快速收敛
            ArrayList<IrBasicBlock> blocks = func.getBasicBlocks();
            for (int i = blocks.size() - 1; i >= 0; i--) {
                IrBasicBlock bb = blocks.get(i);

                // --- 计算 LiveOut ---
                // LiveOut[bb] = Union( LiveIn[succ] ) + PhiUses[succ]
                Set<IrValue> newLiveOut = new HashSet<>();

                for (IrBasicBlock succ : bb.getSuccessors()) {
                    // A. 加入后继块的 LiveIn
                    newLiveOut.addAll(liveIn.get(succ));

                    // B. 处理 Phi 指令的依赖
                    // Phi 指令的操作数是在前驱块（即当前块 bb）末尾活跃的
                    for (Instruction instr : succ.getInstructions()) {
                        if (instr instanceof PhiInstr) {
                            PhiInstr phi = (PhiInstr) instr;
                            ArrayList<IrBasicBlock> incomingBlocks = phi.getIncomingBlocks();
                            ArrayList<IrValue> incomingValues = phi.getIncomingValues();

                            // 检查 Phi 是否引用了来自 bb 的值
                            for (int k = 0; k < incomingBlocks.size(); k++) {
                                if (incomingBlocks.get(k) == bb) {
                                    IrValue val = incomingValues.get(k);
                                    // 只有指令或参数才参与活跃性分析（忽略常量）
                                    if (val instanceof Instruction || val instanceof midend.LLVM.value.IrParameter) {
                                        newLiveOut.add(val);
                                    }
                                    break;
                                }
                            }
                        } else {
                            // Phi 指令一定在块头，遇到非 Phi 停止
                            break;
                        }
                    }
                }

                // --- 计算 LiveIn ---
                // LiveIn[bb] = Use[bb] U (LiveOut[bb] - Def[bb])
                Set<IrValue> newLiveIn = new HashSet<>(newLiveOut);
                newLiveIn.removeAll(def.get(bb));
                newLiveIn.addAll(use.get(bb));

                // 检查是否收敛
                if (!newLiveIn.equals(liveIn.get(bb)) || !newLiveOut.equals(liveOut.get(bb))) {
                    liveIn.put(bb, newLiveIn);
                    liveOut.put(bb, newLiveOut);
                    changed = true;
                }
            }
        }
    }

    /**
     * 计算基本块内部的 Use 和 Def 集合
     */
    private void calcUseDef(IrBasicBlock bb) {
        Set<IrValue> bbDef = new HashSet<>();
        Set<IrValue> bbUse = new HashSet<>();

        for (Instruction instr : bb.getInstructions()) {
            // 1. 处理 Use
            // 注意：Phi 指令的操作数不属于当前块的 Use，而是属于前驱块的 LiveOut
            // 所以这里如果是 Phi，getOperands 应该返回空，或者我们手动跳过 Phi 的操作数检查
            if (!(instr instanceof PhiInstr)) {
                for (IrValue op : getOperands(instr)) {
                    // 只有变量（指令或参数）才算 Use
                    if (op instanceof Instruction || op instanceof midend.LLVM.value.IrParameter) {
                        // 如果该变量在当前块尚未定义，则记为 Upward Exposed Use
                        if (!bbDef.contains(op)) {
                            bbUse.add(op);
                        }
                    }
                }
            }

            // 2. 处理 Def
            // 只要指令产生值（非 Void），就是 Def
            if (!instr.irType.isVoid()) {
                bbDef.add(instr);
            }
        }

        use.put(bb, bbUse);
        def.put(bb, bbDef);
    }

    /**
     * 获取指令的所有操作数 (Input Values)
     * 这是一个比较全的版本，防止遗漏 Call 参数等
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
            ops.add(((GepInstr) instr).getIndice());
        } else if (instr instanceof ZextInstr) {
            ops.add(((ZextInstr) instr).getOperand(0));
        } else if (instr instanceof TruncInstr) {
            ops.add(((TruncInstr) instr).getOperand(0));
        } else if (instr instanceof PrintIntInstr) {
            ops.add(((PrintIntInstr) instr).getPrintValue());
        } else if (instr instanceof PrintStrInstr) {
            ops.add(((PrintStrInstr) instr).getPrintValue());
        } else if (instr instanceof PhiInstr) {
            // Phi 的操作数由外部逻辑处理 (calcUseDef 跳过, LiveOut 计算包含)
            ops.addAll(((PhiInstr) instr).getIncomingValues());
        }

        return ops;
    }
}