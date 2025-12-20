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
        Set<AluInst> toOptimize = new HashSet<>();
        
        for (IrBasicBlock bb : getLoopBlocks(loopHeader)) {
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
            }
        }
        
        // 对每个 mul 创建新的归纳变量
        for (AluInst mul : toOptimize) {
            int mulConst = getMulConstant(mul, iv.phi);
            if (mulConst != 0) {
                // 创建 sum = phi(init*mulConst, sum + step*mulConst)
                // 替换 mul 的使用为 sum
                // 这个变换需要修改 IR，比较复杂
                // 暂时标记找到的优化机会
            }
        }
    }
    
    private Set<IrBasicBlock> getLoopBlocks(IrBasicBlock header) {
        // 简单实现：返回 header 自身
        Set<IrBasicBlock> blocks = new HashSet<>();
        blocks.add(header);
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
