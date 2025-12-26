package midend.Optimization;

import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.DominatorTree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GlobalValueNumbering {
    // 记录 HashKey -> 指令 的映射
    private final Map<String, Instruction> valueTable = new HashMap<>();

    // 简单的死代码消除标记
    private final Set<Instruction> deadInstructions = new HashSet<>();
    private DominatorTree domTree;

    public void run(IrModule module) {
        boolean changed = true;
        int passes = 0;
        int maxPasses = 5;

        while (changed && passes++ < maxPasses) {
            changed = false;
            for (IrFunction func : module.getFunctions()) {
                if (func.getBasicBlocks().isEmpty())
                    continue;

                midend.SSA.CfgBuilder.build(func);
                domTree = new DominatorTree(func);

                if (runOnFunction(func)) {
                    changed = true;
                }
            }
        }
    }

    private boolean runOnFunction(IrFunction func) {
        valueTable.clear();
        deadInstructions.clear();

        for (IrBasicBlock bb : func.getBasicBlocks()) {
            // 每个基本块开始时，清除所有 Load 的 hash（保守处理跨块情况）
            valueTable.entrySet().removeIf(e -> e.getKey().startsWith("LOAD_"));

            for (Instruction instr : bb.getInstructions()) {
                // 遇到 Store 指令时，使所有 Load hash 失效（保守策略）
                if (instr instanceof StoreInstr) {
                    valueTable.entrySet().removeIf(e -> e.getKey().startsWith("LOAD_"));
                    continue;
                }

                // 遇到 Call 指令时，使所有 Load hash 失效（函数可能修改内存）
                if (instr instanceof CallInstr) {
                    valueTable.entrySet().removeIf(e -> e.getKey().startsWith("LOAD_"));
                    continue;
                }

                if (instr.isPinned()) {
                    continue; // 关键指令不参与 GVN 去重
                }

                // 尝试 GVN
                String hash = getHashKey(instr);
                if (hash == null)
                    continue;

                if (valueTable.containsKey(hash)) {
                    Instruction leader = valueTable.get(hash);

                    if (checkDominance(leader, instr)) {
                        if (leader != instr) {
                            instr.replaceAllUsesWith(leader);
                            deadInstructions.add(instr);
                        }
                    }
                } else {
                    valueTable.put(hash, instr);
                }
            }
        }

        // 清理被替换的指令
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            bb.getInstructions().removeIf(deadInstructions::contains);
        }

        return !deadInstructions.isEmpty();
    }

    // 生成指令的唯一标识符
    private String getHashKey(Instruction instr) {
        StringBuilder sb = new StringBuilder();

        if (instr instanceof AluInst) {
            AluInst alu = (AluInst) instr;
            String op = alu.getOp();
            IrValue l = alu.getLeft();
            IrValue r = alu.getRight();

            if (alu.isCommutative() && l.irName.compareTo(r.irName) > 0) {
                // 交换律：保证顺序一致
                IrValue temp = l;
                l = r;
                r = temp;
            }
            sb.append(op).append("_").append(getValueKey(l)).append("_").append(getValueKey(r));

        } else if (instr instanceof CmpInstr) {
            CmpInstr cmp = (CmpInstr) instr;
            String op = cmp.getOp();
            IrValue l = cmp.getLeft();
            IrValue r = cmp.getRight();
            // Cmp 的交换律处理比较复杂 (EQ/NE 可以直接交换，SLT 交换要变 SGT)，这里仅处理 EQ/NE
            if (cmp.isCommutative() && l.irName.compareTo(r.irName) > 0) {
                IrValue temp = l;
                l = r;
                r = temp;
            }
            sb.append("CMP_").append(op).append("_").append(getValueKey(l)).append("_").append(getValueKey(r));

        } else if (instr instanceof GepInstr) {
            GepInstr gep = (GepInstr) instr;
            sb.append("GEP_").append(getValueKey(gep.getPtr())).append("_").append(getValueKey(gep.getIndice()));

        } else if (instr instanceof ZextInstr) {
            ZextInstr zext = (ZextInstr) instr;
            sb.append("ZEXT_").append(getValueKey(zext.getOperand(0)));

        } else if (instr instanceof LoadInstr) {
            // Load CSE - 安全因为我们在 runOnFunction 中追踪 Store 并清除 hash
            LoadInstr load = (LoadInstr) instr;
            IrBasicBlock bb = (IrBasicBlock) load.getParent();
            sb.append("LOAD_").append(bb.irName).append("_").append(getValueKey(load.getPtr()));

        } else {
            return null; // 其他指令暂不支持 GVN
        }

        return sb.toString();
    }

    // 获取值的唯一标识，处理常量
    private String getValueKey(IrValue val) {
        if (val instanceof midend.LLVM.Const.IrConstInt) {
            return "C" + ((midend.LLVM.Const.IrConstInt) val).getValue();
        }
        return val.irName;
    }

    // 简单的支配检查辅助方法
    private boolean checkDominance(Instruction leader, Instruction instr) {
        IrBasicBlock leaderBlock = (IrBasicBlock) leader.getParent();
        IrBasicBlock instrBlock = (IrBasicBlock) instr.getParent();

        // 情况1: 在同一个 Block
        if (leaderBlock == instrBlock) {
            // 因为我们是顺序遍历指令的，如果 Key 存在，说明 leader 一定在 instr 之前
            return true;
        }

        // 情况2: 在不同的 Block，检查块的支配关系
        // 沿着 instrBlock 的 IDom 向上找，看能不能碰到 leaderBlock
        IrBasicBlock runner = instrBlock;
        while (runner != null) {
            if (runner == leaderBlock) return true;
            if (runner == domTree.getIDom(runner)) break; // 防止死循环（如果有自环）
            runner = domTree.getIDom(runner);
        }

        return false;
    }
}