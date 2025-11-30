package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.AluInst;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;

import java.util.HashSet;
import java.util.Set;

public class SimpleConstProp {
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
                    if (instr instanceof AluInst) {
                        AluInst alu = (AluInst) instr;
                        IrValue l = alu.getLeft();
                        IrValue r = alu.getRight();

                        // 如果两个操作数都是常量整数
                        if (l instanceof IrConstInt && r instanceof IrConstInt) {
                            int lv = ((IrConstInt) l).getValue();
                            int rv = ((IrConstInt) r).getValue();
                            int res = 0;
                            boolean computed = true;

                            switch (alu.getOp()) {
                                case "ADD":
                                    res = lv + rv;
                                    break;
                                case "SUB":
                                    res = lv - rv;
                                    break;
                                case "MUL":
                                    res = lv * rv;
                                    break;
                                case "SDIV":
                                    if (rv != 0) res = lv / rv;
                                    else computed = false;
                                    break;
                                case "SREM":
                                    if (rv != 0) res = lv % rv;
                                    else computed = false;
                                    break;
                                // ... 其他操作符 AND, OR, XOR ...
                                default:
                                    computed = false;
                            }

                            if (computed) {
                                // 用常量替换该指令的所有使用者
                                IrConstInt constRes = new IrConstInt(res);
                                alu.replaceAllUsesWith(constRes);
                                dead.add(alu);
                                changed = true;
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