package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.*;

/**
 * 循环强度削减 (Loop Strength Reduction)
 * 将循环中的 i * C 替换为累加 sum += C
 * 
 * 示例:
 * for (i = 0; i < n; i++) {
 *     a[i] = ...  // 实际上是 base + i * 4
 * }
 * 变为:
 * ptr = base;
 * for (i = 0; i < n; i++) {
 *     *ptr = ...
 *     ptr += 4;
 * }
 */
public class LoopStrengthReduction {
    
    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            runOnFunction(func);
        }
    }
    
    private void runOnFunction(IrFunction func) {
        // 找到所有循环头（有 phi 节点且有回边的块）
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            List<PhiInstr> phis = new ArrayList<>();
            for (Instruction instr : bb.getInstructions()) {
                if (instr instanceof PhiInstr) {
                    phis.add((PhiInstr) instr);
                }
            }
            
            // 对于每个 phi，检查是否是归纳变量 (i = phi(0, i + 1))
            for (PhiInstr phi : phis) {
                InductionVar iv = analyzeInductionVar(phi);
                if (iv != null) {
                    // 找到所有使用 iv 的乘法 (i * const)
                    optimizeMultiplications(bb, iv);
                }
            }
        }
    }
    
    // 分析是否是基本归纳变量: i = phi(init, i + step)
    private InductionVar analyzeInductionVar(PhiInstr phi) {
        if (phi.getIncomingValues().size() != 2) return null;
        
        IrValue v0 = phi.getIncomingValues().get(0);
        IrValue v1 = phi.getIncomingValues().get(1);
        
        // 检查模式: phi(const, phi + const) 或 phi(phi + const, const)
        InductionVar iv = tryMatchIV(phi, v0, v1);
        if (iv != null) return iv;
        return tryMatchIV(phi, v1, v0);
    }
    
    private InductionVar tryMatchIV(PhiInstr phi, IrValue init, IrValue update) {
        if (!(init instanceof IrConstInt)) return null;
        if (!(update instanceof AluInst)) return null;
        
        AluInst alu = (AluInst) update;
        if (!alu.getOp().equals("ADD")) return null;
        
        IrValue left = alu.getLeft();
        IrValue right = alu.getRight();
        
        // 检查是 phi + const 还是 const + phi
        if (left == phi && right instanceof IrConstInt) {
            return new InductionVar(phi, ((IrConstInt) init).getValue(), 
                                    ((IrConstInt) right).getValue(), alu);
        }
        if (right == phi && left instanceof IrConstInt) {
            return new InductionVar(phi, ((IrConstInt) init).getValue(),
                                    ((IrConstInt) left).getValue(), alu);
        }
        return null;
    }
    
    private void optimizeMultiplications(IrBasicBlock loopHeader, InductionVar iv) {
        // 找到循环体中所有 iv * const 的使用
        List<AluInst> toOptimize = new ArrayList<>();
        List<GepInstr> gepsToOptimize = new ArrayList<>();
        
        for (IrBasicBlock bb : getLoopBlocks(loopHeader, iv)) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr instanceof AluInst) {
                    AluInst alu = (AluInst) instr;
                    if (alu.getOp().equals("MUL")) {
                        // 检查是否是 iv * const 或 const * iv
                        if ((alu.getLeft() == iv.phi && alu.getRight() instanceof IrConstInt) ||
                            (alu.getRight() == iv.phi && alu.getLeft() instanceof IrConstInt)) {
                            toOptimize.add(alu);
                        }
                    }
                }
                // 也处理 GEP 指令：gep ptr, iv -> ptr + iv * elementSize
                if (instr instanceof GepInstr) {
                    GepInstr gep = (GepInstr) instr;
                    if (gep.getIndice() == iv.phi) {
                        gepsToOptimize.add(gep);
                    }
                }
            }
        }
        
        // 对每个 mul 创建新的归纳变量
        for (AluInst mul : toOptimize) {
            int mulConst = getMulConstant(mul, iv.phi);
            if (mulConst == 0)
                continue;

            // 计算新归纳变量的初始值和步长
            int newInit = iv.initValue * mulConst;
            int newStep = iv.stepValue * mulConst;

            // 1. 创建新的 phi: sum = phi(newInit, sum + newStep)
            PhiInstr sumPhi = new PhiInstr(mul.irType, loopHeader);
            sumPhi.setParentBasicBlock(loopHeader);

            // 2. 创建累加指令: sum_next = sum + newStep
            AluInst sumUpdate = new AluInst("ADD", sumPhi, new IrConstInt(newStep));
            sumUpdate.setParentBasicBlock(iv.updateInstr.getParent());

            // 3. 设置 phi 的输入值
            // 找到前驱块和回边块
            for (int i = 0; i < iv.phi.getIncomingBlocks().size(); i++) {
                IrBasicBlock incBlock = iv.phi.getIncomingBlocks().get(i);
                IrValue incVal = iv.phi.getIncomingValues().get(i);

                if (incVal instanceof IrConstInt) {
                    // 这是初始值的边 - 使用 newInit
                    sumPhi.addIncoming(new IrConstInt(newInit), incBlock);
                } else {
                    // 这是回边 - 使用 sumUpdate
                    sumPhi.addIncoming(sumUpdate, incBlock);
                }
            }

            // 4. 在 header 开头插入 phi (在原有 phi 之后)
            int insertIdx = 0;
            for (int i = 0; i < loopHeader.getInstructions().size(); i++) {
                if (!(loopHeader.getInstructions().get(i) instanceof PhiInstr)) {
                    insertIdx = i;
                    break;
                }
                insertIdx = i + 1;
            }
            loopHeader.getInstructions().add(insertIdx, sumPhi);

            // 5. 在 iv update 之后插入 sumUpdate
            IrBasicBlock updateBlock = iv.updateInstr.getParent();
            int updateIdx = updateBlock.getInstructions().indexOf(iv.updateInstr);
            if (updateIdx >= 0) {
                updateBlock.getInstructions().add(updateIdx + 1, sumUpdate);
            }

            // 6. 替换原 mul 的所有使用为 sumPhi
            mul.replaceAllUsesWith(sumPhi);

            // 7. 标记 mul 为死代码 (DCE 会清理)
            // 不直接删除以避免迭代时修改列表
        }
    }
    
    private Set<IrBasicBlock> getLoopBlocks(IrBasicBlock header, InductionVar iv) {
        // 返回循环头和回边来源块
        Set<IrBasicBlock> blocks = new HashSet<>();
        blocks.add(header);
        // 添加包含 updateInstr 的块
        if (iv.updateInstr.getParent() != null) {
            blocks.add(iv.updateInstr.getParent());
        }
        return blocks;
    }
    
    private int getMulConstant(AluInst mul, PhiInstr iv) {
        if (mul.getLeft() == iv && mul.getRight() instanceof IrConstInt) {
            return ((IrConstInt) mul.getRight()).getValue();
        }
        if (mul.getRight() == iv && mul.getLeft() instanceof IrConstInt) {
            return ((IrConstInt) mul.getLeft()).getValue();
        }
        return 0;
    }
    
    private static class InductionVar {
        PhiInstr phi;
        int initValue;
        int stepValue;
        AluInst updateInstr;
        
        InductionVar(PhiInstr phi, int init, int step, AluInst update) {
            this.phi = phi;
            this.initValue = init;
            this.stepValue = step;
            this.updateInstr = update;
        }
    }
}
