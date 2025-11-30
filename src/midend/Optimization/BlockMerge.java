package midend.Optimization;

import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.JumpInstr;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.SSA.CfgBuilder;
import midend.SSA.PhiInstr;

import java.util.ArrayList;

public class BlockMerge {
    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            if (func.getBasicBlocks().isEmpty()) continue;
            runOnFunction(func);
        }
    }

    private void runOnFunction(IrFunction func) {
        boolean changed = true;
        while (changed) {
            changed = false;
            // 必须先构建 CFG 以获取正确的前驱后继信息
            CfgBuilder.build(func);

            // 使用迭代器遍历，方便操作
            ArrayList<IrBasicBlock> blocks = func.getBasicBlocks();
            for (int i = 0; i < blocks.size() - 1; i++) {
                IrBasicBlock bb = blocks.get(i);
                Instruction terminator = bb.getTerminator();

                // 1. 检查 bb 是否无条件跳转
                if (terminator instanceof JumpInstr) {
                    IrBasicBlock succ = ((JumpInstr) terminator).getTargetBlock();

                    // 2. 检查 succ 是否只有 bb 一个前驱
                    // 注意：succ 不能是入口块（虽然入口块通常没有前驱，但合并入口块要小心）
                    if (succ != func.getEntryBlock() && succ.getPredecessors().size() == 1 && succ.getPredecessors().get(0) == bb) {
                        // 3. 检查 succ 中是否有 Phi 节点
                        // 如果 succ 有 Phi，暂不合并
                        if (hasPhi(succ)) continue;

                        // === 执行合并 ===
                        // A. 移除 bb 的终结指令 (Jump)
                        bb.getInstructions().remove(terminator);

                        // B. 将 succ 的所有指令移动到 bb 末尾
                        bb.getInstructions().addAll(succ.getInstructions());

                        // C. 更新指令的父指针
                        for (Instruction instr : succ.getInstructions()) {
                            instr.setParentBasicBlock(bb);
                        }

                        // D. 维护 CFG: succ 的后继现在变成了 bb 的后继

                        // E. 从函数中移除 succ
                        blocks.remove(succ); // 物理移除

                        // F. 替换所有对 succ 的引用
                        // 主要是维护后继块中 Phi 对 succ 的引用 -> 改为 bb
                        replaceBlockReferences(func, succ, bb);

                        changed = true;
                        i--; // 回退一步，继续检查合并后的 bb 是否能合并下一个
                    }
                }
            }
        }
    }

    private boolean hasPhi(IrBasicBlock bb) {
        for (Instruction i : bb.getInstructions()) {
            if (i instanceof PhiInstr) return true;
        }
        return false;
    }

    private void replaceBlockReferences(IrFunction func, IrBasicBlock oldBlock, IrBasicBlock newBlock) {
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr instanceof PhiInstr) {
                    PhiInstr phi = (PhiInstr) instr;
                    ArrayList<IrBasicBlock> blocks = phi.getIncomingBlocks();
                    for (int i = 0; i < blocks.size(); i++) {
                        if (blocks.get(i) == oldBlock) {
                            blocks.set(i, newBlock);
                        }
                    }
                }
            }
        }
    }
}