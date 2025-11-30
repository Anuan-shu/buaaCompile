package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class AluInst extends Instruction {
    private String op;

    public AluInst(String op, IrValue leftValue, IrValue rightValue) {
        super(ValueType.ALU_INST, IrType.INT32, IrBuilder.GetLocalVarName(), InstructionType.ALU);
        this.op = op;
        this.AddUseValue(leftValue);
        this.AddUseValue(rightValue);
    }

    private String transOp(String rawOp) {
        switch (rawOp) {
            case "+":
                return "add";
            case "-":
                return "sub";
            case "*":
                return "mul";
            case "/":
                return "sdiv";
            case "%":
                return "srem";
            case "&&":
                return "and";
            case "||":
                return "or";
            default:
                return "unknown_op";
        }
    }

    public String toString() {
        IrValue leftValue = this.getUseValues().get(0);
        IrValue rightValue = this.getUseValues().get(1);
        return this.irName + " = " + transOp(op) + " i32 " + leftValue.irName + ", " + rightValue.irName;
    }

    public IrValue getLeft() {
        return this.getUseValues().get(0);
    }

    public IrValue getRight() {
        return this.getUseValues().get(1);
    }

    public String getOp() {
        switch (this.op) {
            case "+":
                return "ADD";
            case "-":
                return "SUB";
            case "*":
                return "MUL";
            case "/":
                return "SDIV";
            case "%":
                return "SREM";
            case "&&":
                return "AND";
            case "||":
                return "OR";
            default:
                return "unknown_op";
        }
    }

    @Override
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        if (this.getUseValues().get(0) == oldVal) {
            this.getUseValues().set(0, newVal);
        }
        if (this.getUseValues().get(1) == oldVal) {
            this.getUseValues().set(1, newVal);
        }
    }

    public String getOpDire() {
        return this.op;
    }
}
