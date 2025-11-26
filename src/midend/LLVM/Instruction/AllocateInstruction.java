package midend.LLVM.Instruction;

import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

public class AllocateInstruction extends Instruction {
    private final IrType allocatedType;

    public AllocateInstruction(String irName, IrType allocatedType) {
        super(ValueType.ALLOCA_INST, new IrPointer(allocatedType), irName, InstructionType.ALLOCATE);
        this.allocatedType = allocatedType;
    }

    public IrType getAllocatedType() {
        return allocatedType;
    }

    public String toString() {
        return this.irName + " = alloca " + this.allocatedType;
    }
}
