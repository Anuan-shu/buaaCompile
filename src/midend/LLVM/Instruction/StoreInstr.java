package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

//store i32 %2, i32* %1
public class StoreInstr extends Instruction {
    public StoreInstr(IrValue valueValue, IrValue addressValue) {
        super(ValueType.STORE_INST, IrType.VOID, "store", InstructionType.STORE);
        this.AddUseValue(valueValue);
        this.AddUseValue(addressValue);
    }

    public String toString() {
        IrValue value = this.getUseValues().get(0);
        IrValue address = this.getUseValues().get(1);
        return "store " + value.irType.toString() + " " + value.irName + ", " + address.irType.toString() + " " + address.irName;
    }

    public IrValue getVal() {
        return this.getUseValues().get(0);
    }

    public IrValue getPtr() {
        return this.getUseValues().get(1);
    }
}
