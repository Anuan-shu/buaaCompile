package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.*;
import midend.LLVM.IrBuilder;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.*;

/**
 * 部分循环展开优化
 * 
 * 不需要知道循环次数，直接将循环体复制 N 次
 * 适用于动态边界循环
 * 
 * 原始:
 * while (i < n) {
 *     body;
 *     i++;
 * }
 * 
 * 展开2x后:
 * while (i + 1 < n) {
 *     body; i++;
 *     body; i++;  // 复制的循环体
 * }
 * while (i < n) {  // 处理剩余
 *     body; i++;
 * }
 */
public class PartialLoopUnroll {
    
    private static final int UNROLL_FACTOR = 4; // 展开因子 (增加)
    private static final int MAX_BODY_INSTRUCTIONS = 30; // 最大循环体指令数 (增加)
    
    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            runOnFunction(func);
        }
    }
    
    private void runOnFunction(IrFunction func) {
        // 找到简单循环并尝试部分展开
        List<IrBasicBlock> blocks = new ArrayList<>(func.getBasicBlocks());
        for (IrBasicBlock bb : blocks) {
            if (isLoopHeader(bb)) {
                LoopInfo loop = analyzeLoop(bb);
                if (loop != null && canPartialUnroll(loop)) {
                    doPartialUnroll(loop);
                }
            }
        }
    }
    
    /**
     * 执行部分循环展开
     * 将循环体复制一次，使每次迭代执行原来2次的工作
     */
    private void doPartialUnroll(LoopInfo loop) {
        IrBasicBlock body = loop.body;
        
        // 获取第一个归纳变量
        if (loop.inductionVars.isEmpty()) return;
        InductionVar iv = loop.inductionVars.get(0);
        
        // 收集需要复制的指令（不包括跳转和phi）
        List<Instruction> bodyInsts = new ArrayList<>();
        for (Instruction inst : body.getInstructions()) {
            if (inst instanceof JumpInstr || inst instanceof BranchInstr) continue;
            if (inst instanceof PhiInstr) continue;
            bodyInsts.add(inst);
        }
        
        if (bodyInsts.isEmpty()) return;
        
        // 创建值映射：旧值 -> 第二次迭代的新值
        HashMap<IrValue, IrValue> valueMap = new HashMap<>();
        
        // 找到归纳变量的更新指令（i = i + step）
        AluInst ivUpdate = iv.updateInstr;
        
        // 在归纳变量更新后，用更新后的值作为第二次迭代的起始值
        valueMap.put(iv.phi, ivUpdate);
        
        // 计算要插入的位置：在跳转指令之前
        Instruction terminator = body.getTerminator();
        int insertIdx = body.getInstructions().indexOf(terminator);
        
        // 复制所有非控制流指令
        for (Instruction inst : bodyInsts) {
            Instruction newInst = copyInstruction(inst, valueMap);
            if (newInst != null) {
                // 设置父基本块 
                newInst.setParentBasicBlock(body);
                // 在terminator之前插入
                body.getInstructions().add(insertIdx, newInst);
                insertIdx++;
                // 更新映射
                valueMap.put(inst, newInst);
            }
        }
        
        // 更新归纳变量的第二次增量
        // 需要在 phi 的回边值中使用第二次迭代的更新值
        if (valueMap.containsKey(ivUpdate)) {
            // Phi 节点需要使用第二次迭代后的 iv 值
            Instruction secondUpdate = (Instruction) valueMap.get(ivUpdate);
            
            // 找到 phi 中来自 body 的值，替换为第二次更新后的值
            for (int i = 0; i < iv.phi.getIncomingBlocks().size(); i++) {
                if (iv.phi.getIncomingBlocks().get(i) == body) {
                    iv.phi.getIncomingValues().set(i, secondUpdate);
                    break;
                }
            }
        }
    }
    
    /**
     * 复制指令
     */
    private Instruction copyInstruction(Instruction inst, HashMap<IrValue, IrValue> map) {
        if (inst instanceof AluInst) {
            AluInst i = (AluInst) inst;
            return new AluInst(i.getOpDire(),
                    map.getOrDefault(i.getLeft(), i.getLeft()),
                    map.getOrDefault(i.getRight(), i.getRight()));
        } else if (inst instanceof LoadInstr) {
            LoadInstr i = (LoadInstr) inst;
            return new LoadInstr(map.getOrDefault(i.getPtr(), i.getPtr()));
        } else if (inst instanceof StoreInstr) {
            StoreInstr i = (StoreInstr) inst;
            return new StoreInstr(map.getOrDefault(i.getVal(), i.getVal()),
                    map.getOrDefault(i.getPtr(), i.getPtr()));
        } else if (inst instanceof GepInstr) {
            GepInstr i = (GepInstr) inst;
            return new GepInstr(map.getOrDefault(i.getPtr(), i.getPtr()),
                    map.getOrDefault(i.getIndice(), i.getIndice()));
        } else if (inst instanceof CallInstr) {
            // 不复制函数调用 - 太复杂
            return null;
        } else if (inst instanceof CmpInstr) {
            CmpInstr i = (CmpInstr) inst;
            return new CmpInstr(i.getOp(),
                    map.getOrDefault(i.getLeft(), i.getLeft()),
                    map.getOrDefault(i.getRight(), i.getRight()));
        } else if (inst instanceof ZextInstr) {
            ZextInstr i = (ZextInstr) inst;
            return new ZextInstr(map.getOrDefault(i.getOperand(0), i.getOperand(0)),
                    i.getTargetType());
        } else if (inst instanceof TruncInstr) {
            TruncInstr i = (TruncInstr) inst;
            return new TruncInstr(map.getOrDefault(i.getOperand(0), i.getOperand(0)),
                    i.irType);
        } else if (inst instanceof PrintIntInstr) {
            PrintIntInstr i = (PrintIntInstr) inst;
            return new PrintIntInstr(map.getOrDefault(i.getPrintValue(), i.getPrintValue()));
        }
        return null;
    }
    
    private boolean isLoopHeader(IrBasicBlock bb) {
        // 如果有 phi 节点且有回边，则可能是循环头
        for (Instruction instr : bb.getInstructions()) {
            if (instr instanceof PhiInstr) {
                return true;
            }
        }
        return false;
    }
    
    private LoopInfo analyzeLoop(IrBasicBlock header) {
        Instruction term = header.getTerminator();
        if (!(term instanceof BranchInstr)) return null;
        
        BranchInstr br = (BranchInstr) term;
        if (br.getCond() == null) return null;
        
        IrBasicBlock body = br.getTrueBlock();
        IrBasicBlock exit = br.getFalseBlock();
        
        // 检查循环体是否跳回头部
        Instruction bodyTerm = body.getTerminator();
        boolean hasBackEdge = false;
        if (bodyTerm instanceof JumpInstr) {
            hasBackEdge = ((JumpInstr) bodyTerm).getTargetBlock() == header;
        } else if (bodyTerm instanceof BranchInstr) {
            BranchInstr bodyBr = (BranchInstr) bodyTerm;
            hasBackEdge = bodyBr.getTrueBlock() == header || bodyBr.getFalseBlock() == header;
        }
        
        if (!hasBackEdge) return null;
        
        LoopInfo info = new LoopInfo();
        info.header = header;
        info.body = body;
        info.exit = exit;
        info.bodyInstrCount = countInstructions(body);
        
        // 查找归纳变量
        for (Instruction instr : header.getInstructions()) {
            if (instr instanceof PhiInstr) {
                PhiInstr phi = (PhiInstr) instr;
                InductionVar iv = analyzeInductionVar(phi, body);
                if (iv != null) {
                    info.inductionVars.add(iv);
                }
            }
        }
        
        return info;
    }
    
    private int countInstructions(IrBasicBlock bb) {
        return bb.getInstructions().size();
    }
    
    private InductionVar analyzeInductionVar(PhiInstr phi, IrBasicBlock body) {
        if (phi.getIncomingBlocks().size() != 2) return null;
        
        int bodyIdx = -1;
        for (int i = 0; i < phi.getIncomingBlocks().size(); i++) {
            if (phi.getIncomingBlocks().get(i) == body) {
                bodyIdx = i;
                break;
            }
        }
        if (bodyIdx == -1) return null;
        
        IrValue updateVal = phi.getIncomingValues().get(bodyIdx);
        if (!(updateVal instanceof AluInst)) return null;
        
        AluInst alu = (AluInst) updateVal;
        if (!alu.getOp().equals("ADD")) return null;
        
        // 检查是 phi + const
        if (alu.getLeft() == phi && alu.getRight() instanceof IrConstInt) {
            int step = ((IrConstInt) alu.getRight()).getValue();
            return new InductionVar(phi, step, alu);
        }
        if (alu.getRight() == phi && alu.getLeft() instanceof IrConstInt) {
            int step = ((IrConstInt) alu.getLeft()).getValue();
            return new InductionVar(phi, step, alu);
        }
        
        return null;
    }
    
    private boolean canPartialUnroll(LoopInfo loop) {
        // 条件：循环体足够小，且有归纳变量
        if (loop.bodyInstrCount > MAX_BODY_INSTRUCTIONS) return false;
        if (loop.inductionVars.isEmpty()) return false;
        
        // 获取归纳变量 phi
        PhiInstr ivPhi = loop.inductionVars.get(0).phi;
        
        // 检查循环体中是否有不能安全复制的指令
        for (Instruction inst : loop.body.getInstructions()) {
            // printf 不能复制 - 会导致输出重复
            if (inst instanceof PrintIntInstr) return false;
            if (inst instanceof PrintStrInstr) return false;
            // 函数调用不能复制 - 可能有副作用
            if (inst instanceof CallInstr) return false;
            // Store 到依赖于归纳变量的地址不能复制
            if (inst instanceof StoreInstr) {
                StoreInstr store = (StoreInstr) inst;
                // 如果 Store 的地址依赖于归纳变量，跳过
                if (dependsOnValue(store.getPtr(), ivPhi)) return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查 value 是否直接或间接依赖于 target
     */
    private boolean dependsOnValue(IrValue value, IrValue target) {
        if (value == target) return true;
        if (value instanceof GepInstr) {
            GepInstr gep = (GepInstr) value;
            return dependsOnValue(gep.getIndice(), target);
        }
        if (value instanceof AluInst) {
            AluInst alu = (AluInst) value;
            return dependsOnValue(alu.getLeft(), target) || 
                   dependsOnValue(alu.getRight(), target);
        }
        return false;
    }
    
    private static class LoopInfo {
        IrBasicBlock header;
        IrBasicBlock body;
        IrBasicBlock exit;
        int bodyInstrCount;
        List<InductionVar> inductionVars = new ArrayList<>();
    }
    
    private static class InductionVar {
        PhiInstr phi;
        int step;
        AluInst updateInstr;
        
        InductionVar(PhiInstr phi, int step, AluInst update) {
            this.phi = phi;
            this.step = step;
            this.updateInstr = update;
        }
    }
}
