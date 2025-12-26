package midend.Optimization;

import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.*;

/**
 * Copy Coalescing 优化
 * 1. 消除单源 Phi 节点 - 如果 Phi 的所有输入都是同一个值
 * 2. 消除冗余 Phi - 如果 Phi 的一个输入是它自己
 * 3. 传播 Phi 值 - 简化 Phi 链
 */
public class CopyCoalescing {
    
    public void run(IrModule module) {
        boolean changed = true;
        int passes = 0;
        int maxPasses = 10;
        
        while (changed && passes++ < maxPasses) {
            changed = false;
            for (IrFunction func : module.getFunctions()) {
                if (func.getBasicBlocks().isEmpty()) continue;
                if (runOnFunction(func)) {
                    changed = true;
                }
            }
        }
    }
    
    private boolean runOnFunction(IrFunction func) {
        boolean changed = false;
        
        // 遍历所有基本块
        for (IrBasicBlock bb : new ArrayList<>(func.getBasicBlocks())) {
            List<Instruction> toRemove = new ArrayList<>();
            
            for (Instruction instr : new ArrayList<>(bb.getInstructions())) {
                if (instr instanceof PhiInstr) {
                    PhiInstr phi = (PhiInstr) instr;
                    IrValue replacement = canCoalesce(phi);
                    
                    if (replacement != null) {
                        // 用单一值替换 Phi 的所有使用
                        phi.replaceAllUsesWith(replacement);
                        toRemove.add(phi);
                        changed = true;
                    }
                }
            }
            
            bb.getInstructions().removeAll(toRemove);
        }
        
        return changed;
    }
    
    /**
     * 检查 Phi 是否可以合并
     * 返回替换值，或 null 如果不能合并
     */
    private IrValue canCoalesce(PhiInstr phi) {
        List<IrValue> incomingValues = phi.getIncomingValues();
        
        if (incomingValues.isEmpty()) return null;
        
        // 找到第一个非自引用的值
        IrValue candidate = null;
        for (IrValue val : incomingValues) {
            if (val != phi) {
                if (candidate == null) {
                    candidate = val;
                } else if (candidate != val) {
                    // 有多个不同的值，不能合并
                    return null;
                }
            }
        }
        
        // 如果所有输入都是同一个值（或自引用），可以合并
        return candidate;
    }
}
