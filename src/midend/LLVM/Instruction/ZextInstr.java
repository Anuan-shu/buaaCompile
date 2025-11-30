package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class ZextInstr extends Instruction {
    private IrType targetType;

    public ZextInstr(IrValue originValue, IrType targetType) {
        super(ValueType.ZEXT_INST, targetType, IrBuilder.GetLocalVarName(), InstructionType.ZEXT);
        this.targetType = targetType;
        this.AddUseValue(originValue);
    }

    public String toString() {
        return this.irName + " = zext " + this.getUseValues().get(0).irType.toString() + " " + this.getUseValues().get(0).irName + " to " + this.targetType.toString();
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
