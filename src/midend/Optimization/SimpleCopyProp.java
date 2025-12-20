package midend.Optimization;

import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;

import java.util.HashSet;
import java.util.Set;

/**
 * IR 级别的拷贝传播优化
 * 
 * 1. 消除无效的 zext：zext i32 %x to i32 -> %x
 * 2. 消除无效的 trunc：trunc i32 %x to i32 -> %x
 * 3. 传递死代码给 DCE 处理
 */
public class SimpleCopyProp {
    
    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            runOnFunction(func);
        }
    }
    
    private void runOnFunction(IrFunction func) {
        boolean changed = true;
        
        while (changed) {
            changed = false;
            Set<Instruction> dead = new HashSet<>();
            
            for (IrBasicBlock bb : func.getBasicBlocks()) {
                for (Instruction instr : bb.getInstructions()) {
                    
                    // 1. 消除无效的 zext (i32 -> i32)
                    if (instr instanceof ZextInstr) {
                        ZextInstr zext = (ZextInstr) instr;
                        IrValue operand = zext.getOperand(0);
                        
                        // 如果源类型和目标类型相同，直接替换
                        if (operand.irType.equals(zext.getTargetType())) {
                            zext.replaceAllUsesWith(operand);
                            dead.add(zext);
                            changed = true;
                        }
                        // 特殊情况：zext i1 to i32 后面如果又 trunc 回来，可以消除
                    }
                    
                    // 2. 消除无效的 trunc (i32 -> i32)
                    if (instr instanceof TruncInstr) {
                        TruncInstr trunc = (TruncInstr) instr;
                        IrValue operand = trunc.getOperand(0);
                        
                        // 如果源类型和目标类型相同，直接替换
                        if (operand.irType.equals(trunc.irType)) {
                            trunc.replaceAllUsesWith(operand);
                            dead.add(trunc);
                            changed = true;
                        }
                    }
                    
                    // 3. 传递 zext(zext(x)) -> zext(x) 的情况
                    if (instr instanceof ZextInstr) {
                        ZextInstr zext = (ZextInstr) instr;
                        IrValue operand = zext.getOperand(0);
                        
                        if (operand instanceof ZextInstr) {
                            // zext(zext(x)) 可以简化
                            ZextInstr innerZext = (ZextInstr) operand;
                            IrValue originalVal = innerZext.getOperand(0);
                            
                            // 创建新的 zext 直接从原始值到最终类型
                            // 如果最终类型是 i32，直接用原始值的 zext
                            if (zext.getTargetType().equals(IrType.INT32)) {
                                // 外层 zext 可以直接使用内层的操作数
                        
                            }
                        }
                    }
                }
            }
            
            // 删除死指令
            for (IrBasicBlock bb : func.getBasicBlocks()) {
                bb.getInstructions().removeAll(dead);
            }
        }
    }
}
