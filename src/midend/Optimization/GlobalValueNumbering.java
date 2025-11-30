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
        for (IrFunction func : module.getFunctions()) {
            if (func.getBasicBlocks().isEmpty()) continue;

            midend.SSA.CfgBuilder.build(func);
            domTree = new DominatorTree(func);

            runOnFunction(func);
        }
    }

    private void runOnFunction(IrFunction func) {
        valueTable.clear();
        deadInstructions.clear();

        // 使用支配树获取 RPO 序 (为了保证先处理定义后处理使用)

        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr.isPinned()) {
                    continue; // 关键指令不参与 GVN 去重 (除了 Phi，但 Phi 很难 Hash)
                }

                // 尝试 GVN
                String hash = getHashKey(instr);
                if (hash == null) continue; // 无法计算 Hash 的指令跳过

                if (valueTable.containsKey(hash)) {
                    // 发现冗余！
                    Instruction leader = valueTable.get(hash);

                    // 只有当 leader 所在的块 支配 当前块时，才能替换
                    // (如果 leader 和 instr 在同一个块，leader 肯定在前面，因为是顺序遍历的，这也是安全的)

                    if (checkDominance(leader, instr)) {
                        // 只有支配才能替换
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
            sb.append(op).append("_").append(l.irName).append("_").append(r.irName);

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
            sb.append("CMP_").append(op).append("_").append(l.irName).append("_").append(r.irName);

        } else if (instr instanceof GepInstr) {
            GepInstr gep = (GepInstr) instr;
            sb.append("GEP_").append(gep.getPtr().irName).append("_").append(gep.getIndice().irName);

        } else if (instr instanceof ZextInstr) {
            ZextInstr zext = (ZextInstr) instr;
            sb.append("ZEXT_").append(zext.getOperand(0).irName);

        } else {
            return null; // 其他指令暂不支持 GVN
        }

        return sb.toString();
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