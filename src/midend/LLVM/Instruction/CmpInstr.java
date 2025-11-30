package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class CmpInstr extends Instruction {
    private String op;

    public CmpInstr(String op, IrValue leftValue, IrValue rightValue) {
        super(ValueType.COMPARE_INST, IrType.INT1, IrBuilder.GetLocalVarName(), InstructionType.CMP);
        this.op = op;
        this.AddUseValue(leftValue);
        this.AddUseValue(rightValue);
    }

    private String transOp(String rawOp) {
        switch (rawOp) {
            case "<":
                return "slt";
            case "<=":
                return "sle";
            case ">":
                return "sgt";
            case ">=":
                return "sge";
            case "==":
                return "eq";
            case "!=":
                return "ne";
            default:
                return "unknown_op";
        }
    }

    public String toString() {
        IrValue leftValue = this.getUseValues().get(0);
        IrValue rightValue = this.getUseValues().get(1);
        return this.irName + " = icmp " + transOp(op) + " i32 " + leftValue.irName + ", " + rightValue.irName;
    }

    public IrValue getLeft() {
        return this.getUseValues().get(0);
    }

    public IrValue getRight() {
        return this.getUseValues().get(1);
    }

    public String getOp() {
        switch (op) {
            case "<":
                return "LT";
            case "<=":
                return "LE";
            case ">":
                return "GT";
            case ">=":
                return "GE";
            case "==":
                return "EQ";
            case "!=":
                return "NE";
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
}
