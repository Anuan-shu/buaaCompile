package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class TruncInstr extends Instruction {
    private IrType targetType;

    public TruncInstr(IrValue originValue, IrType targetType) {
        super(ValueType.TRUNC_INST, targetType, IrBuilder.GetLocalVarName(), InstructionType.TRUNC);
        this.targetType = targetType;
        this.AddUseValue(originValue);
    }

    public String toString() {
        return this.irName + " = trunc " + this.getUseValues().get(0).irType.toString() + " " + this.getUseValues().get(0).irName + " to " + targetType.toString();
    }

    public IrValue getOperand(int i) {
        return this.getUseValues().get(i);
    }

    @Override
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        if (this.getUseValues().get(0) == oldVal) {
            this.getUseValues().set(0, newVal);
        }
    }
}
