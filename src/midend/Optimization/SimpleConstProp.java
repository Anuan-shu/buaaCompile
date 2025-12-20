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

                        // 如果两个操作数都是常量整数 -> 常量折叠
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
                                default:
                                    computed = false;
                            }

                            if (computed) {
                                IrConstInt constRes = new IrConstInt(res);
                                alu.replaceAllUsesWith(constRes);
                                dead.add(alu);
                                changed = true;
                            }
                        }
                        // 代数简化：右操作数为常量
                        else if (r instanceof IrConstInt) {
                            int rv = ((IrConstInt) r).getValue();
                            boolean simplified = false;
                            IrValue replacement = null;

                            switch (alu.getOp()) {
                                case "ADD":
                                    if (rv == 0) {
                                        replacement = l;
                                        simplified = true;
                                    } // x + 0 -> x
                                    break;
                                case "SUB":
                                    if (rv == 0) {
                                        replacement = l;
                                        simplified = true;
                                    } // x - 0 -> x
                                    break;
                                case "MUL":
                                    if (rv == 1) {
                                        replacement = l;
                                        simplified = true;
                                    } // x * 1 -> x
                                    else if (rv == 0) {
                                        replacement = new IrConstInt(0);
                                        simplified = true;
                                    } // x * 0 -> 0
                                    break;
                                case "SDIV":
                                    if (rv == 1) {
                                        replacement = l;
                                        simplified = true;
                                    } // x / 1 -> x
                                    break;
                                case "SREM":
                                    if (rv == 1 || rv == -1) {
                                        replacement = new IrConstInt(0);
                                        simplified = true;
                                    } // x % 1 = 0, x % -1 = 0
                                    break;
                            }

                            if (simplified && replacement != null) {
                                alu.replaceAllUsesWith(replacement);
                                dead.add(alu);
                                changed = true;
                            }
                        }
                        // 代数简化：左操作数为常量
                        else if (l instanceof IrConstInt) {
                            int lv = ((IrConstInt) l).getValue();
                            boolean simplified = false;
                            IrValue replacement = null;

                            switch (alu.getOp()) {
                                case "ADD":
                                    if (lv == 0) {
                                        replacement = r;
                                        simplified = true;
                                    } // 0 + x -> x
                                    break;
                                case "MUL":
                                    if (lv == 1) {
                                        replacement = r;
                                        simplified = true;
                                    } // 1 * x -> x
                                    else if (lv == 0) {
                                        replacement = new IrConstInt(0);
                                        simplified = true;
                                    } // 0 * x -> 0
                                    break;
                            }

                            if (simplified && replacement != null) {
                                alu.replaceAllUsesWith(replacement);
                                dead.add(alu);
                                changed = true;
                            }
                        }
                        // 代数简化：x - x -> 0
                        else if (alu.getOp().equals("SUB") && l == r) {
                            alu.replaceAllUsesWith(new IrConstInt(0));
                            dead.add(alu);
                            changed = true;
                        }
                        // 代数简化：x / x -> 1 (假设 x != 0)
                        else if (alu.getOp().equals("SDIV") && l == r) {
                            alu.replaceAllUsesWith(new IrConstInt(1));
                            dead.add(alu);
                            changed = true;
                        }
                        // 代数简化：x % x -> 0 (假设 x != 0)
                        else if (alu.getOp().equals("SREM") && l == r) {
                            alu.replaceAllUsesWith(new IrConstInt(0));
                            dead.add(alu);
                            changed = true;
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