package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class LoadInstr extends Instruction {

    public LoadInstr(IrValue pointer) {
        super(ValueType.LOAD_INST, ((IrPointer) pointer.irType).targetType, IrBuilder.GetLocalVarName(), InstructionType.LOAD);
        this.AddUseValue(pointer);
    }

    public IrType getPointType() {
        return this.getUseValues().get(0).irType;
    }

    public String toString() {
        IrValue pointer = this.getUseValues().get(0);
        return this.irName + " = load " + this.irType + ", " + pointer.irType + " " + pointer.irName;
    }

    public IrValue getPtr() {
        return this.getUseValues().get(0);
    }
}
