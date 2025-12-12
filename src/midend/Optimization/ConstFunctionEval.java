package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrParameter;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.*;

/**
 * 编译时纯函数求值优化
 * 如果一个函数调用的参数都是编译时常量，且函数是"纯函数"(无副作用)，
 * 则尝试在编译时解释执行该函数，用结果常量替换函数调用。
 * 
 * 这对于 fib(4)、fib(fib(5)+2) 这类调用特别有效。
 */
public class ConstFunctionEval {
    
    // 解释器执行的最大步数，防止无限循环
    private static final int MAX_STEPS = 10000;
    // 递归深度限制
    private static final int MAX_RECURSION_DEPTH = 50;
    
    private int stepCount;
    private int recursionDepth;
    
    public void run(IrModule module) {
        boolean changed = true;
        int passes = 0;
        int maxPasses = 10;
        
        while (changed && passes++ < maxPasses) {
            changed = false;
            for (IrFunction func : new ArrayList<>(module.getFunctions())) {
                if (func.getBasicBlocks().isEmpty()) continue;
                if (runOnFunctionImpl(func)) {
                    changed = true;
                }
            }
        }
    }
    
    private boolean runOnFunctionImpl(IrFunction func) {
        boolean changed = false;
        
        for (IrBasicBlock bb : new ArrayList<>(func.getBasicBlocks())) {
            List<Instruction> toRemove = new ArrayList<>();
            
            for (Instruction instr : new ArrayList<>(bb.getInstructions())) {
                if (instr instanceof CallInstr) {
                    CallInstr call = (CallInstr) instr;
                    IrFunction callee = call.getTargetFunction();
                    
                    // 跳过库函数 (getint, printf 等)
                    if (callee.getBasicBlocks().isEmpty()) continue;
                    
                    // 检查所有参数是否为常量
                    List<Integer> constArgs = new ArrayList<>();
                    boolean allConst = true;
                    for (IrValue arg : call.getParameters()) {
                        if (arg instanceof IrConstInt) {
                            constArgs.add(((IrConstInt) arg).getValue());
                        } else {
                            allConst = false;
                            break;
                        }
                    }
                    
                    if (!allConst) continue;
                    
                    // 检查函数是否为纯函数
                    if (!isPureFunction(callee)) continue;
                    
                    // 尝试编译时求值
                    stepCount = 0;
                    recursionDepth = 0;
                    Integer result = evaluateFunction(callee, constArgs);
                    
                    if (result != null) {
                        // 成功求值，用常量替换调用
                        IrConstInt constResult = new IrConstInt(result);
                        call.replaceAllUsesWith(constResult);
                        toRemove.add(call);
                        changed = true;
                    }
                }
            }
            
            bb.getInstructions().removeAll(toRemove);
        }
        return changed;
    }
    
    /**
     * 检查函数是否为纯函数（无副作用）
     * 纯函数：不调用 IO 函数，不写全局变量，不做内存写操作
     */
    private boolean isPureFunction(IrFunction func) {
        return isPureFunctionImpl(func, new java.util.HashSet<>());
    }
    
    private boolean isPureFunctionImpl(IrFunction func, java.util.Set<String> visited) {
        if (visited.contains(func.irName)) {
            // 已经在检查中，认为是纯的（避免无限递归）
            return true;
        }
        visited.add(func.irName);
        
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                // 禁止 Store 指令（可能写全局变量或数组）
                if (instr instanceof StoreInstr) {
                    return false;
                }
                // 检查函数调用
                if (instr instanceof CallInstr) {
                    CallInstr call = (CallInstr) instr;
                    IrFunction callee = call.getTargetFunction();
                    String name = callee.irName;
                    // 禁止 IO 函数
                    if (name.contains("getint") || name.contains("printf") || 
                        name.contains("putint") || name.contains("putch") || name.contains("putstr")) {
                        return false;
                    }
                    // 递归调用自身是允许的（通过名字比较）
                    // 调用其他用户定义函数时，递归检查
                    if (!callee.getBasicBlocks().isEmpty() && !callee.irName.equals(func.irName)) {
                        if (!isPureFunctionImpl(callee, visited)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * 解释执行函数，返回结果或 null（如果无法求值）
     */
    private Integer evaluateFunction(IrFunction func, List<Integer> args) {
        if (recursionDepth > MAX_RECURSION_DEPTH) return null;
        recursionDepth++;
        
        try {
            // 创建局部变量映射
            Map<IrValue, Integer> locals = new HashMap<>();
            
            // 绑定参数
            ArrayList<IrParameter> params = func.getParameters();
            if (params.size() != args.size()) return null;
            for (int i = 0; i < params.size(); i++) {
                locals.put(params.get(i), args.get(i));
            }
            
            // 从入口块开始执行
            IrBasicBlock currentBlock = func.getBasicBlocks().get(0);
            
            while (currentBlock != null) {
                if (++stepCount > MAX_STEPS) return null;
                
                for (Instruction instr : currentBlock.getInstructions()) {
                    if (++stepCount > MAX_STEPS) return null;
                    
                    if (instr instanceof AluInst) {
                        AluInst alu = (AluInst) instr;
                        Integer left = getValue(alu.getLeft(), locals);
                        Integer right = getValue(alu.getRight(), locals);
                        if (left == null || right == null) return null;
                        
                        int result;
                        switch (alu.getOp()) {
                            case "ADD": result = left + right; break;
                            case "SUB": result = left - right; break;
                            case "MUL": result = left * right; break;
                            case "SDIV": 
                                if (right == 0) return null;
                                result = left / right; 
                                break;
                            case "SREM": 
                                if (right == 0) return null;
                                result = left % right; 
                                break;
                            default: return null;
                        }
                        locals.put(alu, result);
                        
                    } else if (instr instanceof CmpInstr) {
                        CmpInstr cmp = (CmpInstr) instr;
                        Integer left = getValue(cmp.getLeft(), locals);
                        Integer right = getValue(cmp.getRight(), locals);
                        if (left == null || right == null) return null;
                        
                        boolean result;
                        String op = cmp.getOp();
                        switch (op) {
                            case "EQ": result = left.equals(right); break;
                            case "NE": result = !left.equals(right); break;
                            case "LT": result = left < right; break;
                            case "LE": result = left <= right; break;
                            case "GT": result = left > right; break;
                            case "GE": result = left >= right; break;
                            default: return null;
                        }
                        locals.put(cmp, result ? 1 : 0);
                        
                    } else if (instr instanceof ZextInstr) {
                        // 零扩展指令：从 i1 到 i32 等，值本身不变
                        ZextInstr zext = (ZextInstr) instr;
                        Integer operandVal = getValue(zext.getOperand(0), locals);
                        if (operandVal == null) return null;
                        locals.put(zext, operandVal);
                        
                    } else if (instr instanceof BranchInstr) {
                        BranchInstr br = (BranchInstr) instr;
                        if (br.getCond() == null) {
                            // 无条件跳转
                            currentBlock = br.getTrueBlock();
                        } else {
                            Integer cond = getValue(br.getCond(), locals);
                            if (cond == null) return null;
                            currentBlock = (cond != 0) ? br.getTrueBlock() : br.getFalseBlock();
                        }
                        break; // 跳转到新块
                        
                    } else if (instr instanceof JumpInstr) {
                        currentBlock = ((JumpInstr) instr).getTargetBlock();
                        break;
                        
                    } else if (instr instanceof ReturnInstr) {
                        ReturnInstr ret = (ReturnInstr) instr;
                        IrValue retVal = ret.getReturnValue();
                        if (retVal == null) return 0; // void 返回
                        return getValue(retVal, locals);
                        
                    } else if (instr instanceof CallInstr) {
                        CallInstr call = (CallInstr) instr;
                        IrFunction callee = call.getTargetFunction();
                        
                        // 库函数不支持
                        if (callee.getBasicBlocks().isEmpty()) return null;
                        
                        // 收集调用参数
                        List<Integer> callArgs = new ArrayList<>();
                        for (IrValue arg : call.getParameters()) {
                            Integer val = getValue(arg, locals);
                            if (val == null) return null;
                            callArgs.add(val);
                        }
                        
                        // 递归求值
                        Integer callResult = evaluateFunction(callee, callArgs);
                        if (callResult == null) return null;
                        locals.put(call, callResult);
                        
                    } else if (instr instanceof PhiInstr) {
                        // Phi 指令在常量求值中需要特殊处理
                        // 这里简化处理：如果遇到 Phi，放弃求值
                        return null;
                        
                    } else if (instr instanceof AllocateInstruction || instr instanceof LoadInstr || 
                               instr instanceof StoreInstr || instr instanceof GepInstr) {
                        // 内存操作，不支持
                        return null;
                    }
                    // 其他指令忽略或不支持
                }
                
                // 如果没有跳转指令，应该不会到这里
                // 但为安全起见，如果指令列表结束且没有跳转，返回 null
                if (currentBlock.getInstructions().isEmpty()) return null;
                Instruction last = currentBlock.getInstructions().get(currentBlock.getInstructions().size() - 1);
                if (!(last instanceof BranchInstr) && !(last instanceof JumpInstr) && !(last instanceof ReturnInstr)) {
                    return null;
                }
            }
            
            return null;
        } finally {
            recursionDepth--;
        }
    }
    
    private Integer getValue(IrValue val, Map<IrValue, Integer> locals) {
        if (val instanceof IrConstInt) {
            return ((IrConstInt) val).getValue();
        }
        return locals.get(val);
    }
}
