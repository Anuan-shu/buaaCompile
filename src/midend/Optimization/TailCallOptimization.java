package midend.Optimization;

import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;

import java.util.*;

/**
 * 尾调用优化 (Tail Call Optimization)
 * 
 * 识别模式:
 *   %ret = call @func(...)
 *   ret %ret
 * 
 * 如果是递归调用且参数类型匹配，可以转换为:
 *   将参数复制到入口参数位置
 *   jump 到函数入口
 * 
 * 这样可以避免栈增长，将递归转为循环
 */
public class TailCallOptimization {
    
    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            if (func.getBasicBlocks().isEmpty()) continue;
            runOnFunction(func);
        }
    }
    
    private void runOnFunction(IrFunction func) {
        // 查找所有尾调用点
        List<TailCallSite> tailCalls = new ArrayList<>();
        
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            TailCallSite site = findTailCall(bb, func);
            if (site != null) {
                tailCalls.add(site);
            }
        }
        
        // 对每个尾调用进行优化
        for (TailCallSite site : tailCalls) {
            optimizeTailCall(func, site);
        }
    }
    
    /**
     * 查找块中的尾调用模式:
     * 1. call 指令
     * 2. 紧跟 ret 指令，返回值就是 call 的结果
     */
    private TailCallSite findTailCall(IrBasicBlock bb, IrFunction currentFunc) {
        ArrayList<Instruction> instrs = bb.getInstructions();
        if (instrs.size() < 2) return null;
        
        // 检查最后两条指令
        Instruction last = instrs.get(instrs.size() - 1);
        Instruction secondLast = instrs.get(instrs.size() - 2);
        
        // 必须是: call + ret
        if (!(last instanceof ReturnInstr)) return null;
        if (!(secondLast instanceof CallInstr)) return null;
        
        ReturnInstr ret = (ReturnInstr) last;
        CallInstr call = (CallInstr) secondLast;
        
        // 检查返回值是否是 call 的结果
        if (!ret.isReturnVoid()) {
            if (ret.getReturnValue() != call) return null;
        }
        
        // 获取被调用函数
        IrFunction callee = call.getTargetFunction();
        
        // 只处理递归调用（自己调用自己）
        if (callee != currentFunc) return null;
        
        // 检查参数数量是否匹配
        if (call.getParameters().size() != currentFunc.getParameters().size()) {
            return null;
        }
        
        return new TailCallSite(bb, call, ret);
    }
    
    /**
     * 执行尾调用优化:
     * 1. 将 call 的参数复制到 phi 节点（模拟参数传递）
     * 2. 将 call + ret 替换为 jump 到入口块
     */
    private void optimizeTailCall(IrFunction func, TailCallSite site) {
        IrBasicBlock bb = site.block;
        CallInstr call = site.call;
        ReturnInstr ret = site.ret;
        
        // 获取入口块
        IrBasicBlock entryBlock = func.getEntryBlock();
        
        // 如果入口块就是当前块，不能直接优化（会死循环）
        if (entryBlock == bb) return;
        
        // 创建跳转到入口块的指令
        JumpInstr jump = new JumpInstr(entryBlock);
        jump.setParentBasicBlock(bb);
        
        // 获取调用参数
        ArrayList<IrValue> callArgs = call.getParameters();
        ArrayList<midend.LLVM.value.IrParameter> funcParams = func.getParameters();
        
        // 在入口块为每个参数创建 phi 节点（如果还没有）
        // 或者更新现有的 phi 节点
        for (int i = 0; i < funcParams.size(); i++) {
            IrValue param = funcParams.get(i);
            IrValue arg = callArgs.get(i);
            
            // 查找或创建参数对应的 phi
            // 对于尾递归，需要将原参数替换为 phi
            // phi(original_arg from predecessors, call_arg from this block)
            
            // 简化实现：直接在跳转前添加 move 指令（通过 phi 实现）
            // 这需要 entryBlock 有 phi 来接收
            ensureParameterPhi(func, entryBlock, param, bb, arg);
        }
        
        // 移除原来的 call 和 ret，添加 jump
        bb.getInstructions().remove(call);
        bb.getInstructions().remove(ret);
        bb.getInstructions().add(jump);
    }
    
    /**
     * 确保入口块有参数对应的 phi 节点
     */
    private void ensureParameterPhi(IrFunction func, IrBasicBlock entry, 
                                    IrValue param, IrBasicBlock tailBlock, IrValue tailArg) {
        // 检查是否已经有对应的 phi
        for (Instruction instr : entry.getInstructions()) {
            if (instr instanceof midend.SSA.PhiInstr) {
                midend.SSA.PhiInstr phi = (midend.SSA.PhiInstr) instr;
                // 如果这个 phi 已经包含来自 tailBlock 的边，更新它
                for (int i = 0; i < phi.getIncomingBlocks().size(); i++) {
                    if (phi.getIncomingBlocks().get(i) == tailBlock) {
                        // 已经有这条边，不需要添加
                        return;
                    }
                }
            }
        }
        
        // 创建新的 phi 节点
        midend.SSA.PhiInstr paramPhi = new midend.SSA.PhiInstr(param.irType, entry);
        paramPhi.setParentBasicBlock(entry);
        
        // 对于所有原来的前驱块，phi 值是原参数
        // 对于尾调用块，phi 值是调用参数
        for (IrBasicBlock pred : getPredecessors(entry, func)) {
            if (pred == tailBlock) {
                paramPhi.addIncoming(tailArg, pred);
            } else {
                paramPhi.addIncoming(param, pred);
            }
        }
        
        // 添加尾调用块作为新的前驱
        paramPhi.addIncoming(tailArg, tailBlock);
        
        // 在入口块开头插入 phi
        entry.getInstructions().add(0, paramPhi);
        
        // 将原参数的所有使用替换为 phi
        // IrParameter 没有 replaceAllUsesWith，需要手动遍历函数中的使用
        replaceParamUses(func, param, paramPhi);
    }
    
    /**
     * 手动替换参数的所有使用为 phi
     */
    private void replaceParamUses(IrFunction func, IrValue oldParam, midend.SSA.PhiInstr newPhi) {
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr == newPhi) continue; // 不替换 phi 本身
                instr.replaceUse(oldParam, newPhi);
            }
        }
    }
    
    /**
     * 获取基本块的前驱块
     */
    private List<IrBasicBlock> getPredecessors(IrBasicBlock bb, IrFunction func) {
        List<IrBasicBlock> preds = new ArrayList<>();
        for (IrBasicBlock block : func.getBasicBlocks()) {
            Instruction term = block.getTerminator();
            if (term instanceof JumpInstr) {
                if (((JumpInstr) term).getTargetBlock() == bb) {
                    preds.add(block);
                }
            } else if (term instanceof BranchInstr) {
                BranchInstr br = (BranchInstr) term;
                if (br.getTrueBlock() == bb || br.getFalseBlock() == bb) {
                    preds.add(block);
                }
            }
        }
        return preds;
    }
    
    private static class TailCallSite {
        IrBasicBlock block;
        CallInstr call;
        ReturnInstr ret;
        
        TailCallSite(IrBasicBlock block, CallInstr call, ReturnInstr ret) {
            this.block = block;
            this.call = call;
            this.ret = ret;
        }
    }
}
