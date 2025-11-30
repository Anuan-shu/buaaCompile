package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class ReturnInstr extends Instruction {
    private IrValue returnValue;

    public ReturnInstr(IrValue returnValue) {
        super(ValueType.RETURN_INST, IrType.VOID, "return", InstructionType.RETURN);
        this.returnValue = returnValue;
        this.AddUseValue(returnValue);
    }

    public String toString() {
        if (returnValue == null) {
            return "ret void";
        } else {
            return "ret " + returnValue.irType.toString() + " " + returnValue.irName;
        }
    }

    public boolean isReturnVoid() {
        return returnValue == null;
    }

    public IrValue getReturnValue() {
        return returnValue;
    }

    @Override
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        if (this.returnValue == oldVal) {
            this.returnValue = newVal;
        }
    }
}
