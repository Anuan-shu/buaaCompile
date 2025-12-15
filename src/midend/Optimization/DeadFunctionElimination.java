package midend.Optimization;

import midend.LLVM.Instruction.CallInstr;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;

import java.util.HashSet;
import java.util.Set;

/**
 * 死函数消除优化
 * 删除没有被调用的用户定义函数（保留 main）
 */
public class DeadFunctionElimination {
    
    public void run(IrModule module) {
        boolean changed = true;
        
        while (changed) {
            changed = false;
            
            // 1. 收集所有被调用的函数名
            Set<String> calledFunctions = new HashSet<>();
            calledFunctions.add("@main"); // main 总是被保留
            
            for (IrFunction func : module.getFunctions()) {
                if (func.getBasicBlocks().isEmpty()) continue; // 跳过外部函数声明
                
                for (IrBasicBlock bb : func.getBasicBlocks()) {
                    for (Instruction instr : bb.getInstructions()) {
                        if (instr instanceof CallInstr) {
                            CallInstr call = (CallInstr) instr;
                            calledFunctions.add(call.getTargetFunction().irName);
                        }
                    }
                }
            }
            
            // 2. 删除没有被调用的函数
            var iterator = module.getFunctions().iterator();
            while (iterator.hasNext()) {
                IrFunction func = iterator.next();
                // 跳过库函数声明（没有基本块）
                if (func.getBasicBlocks().isEmpty()) continue;
                
                if (!calledFunctions.contains(func.irName)) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
    }
}
