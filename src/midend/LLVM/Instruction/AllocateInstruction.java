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

    public int getAllocatedSize() {
        if (allocatedType.isIntegerType()) {
            return 4;
        }
        // 如果是数组类型，计算总大小
        if (allocatedType.isArrayType()) {
            int elementSize = 4;
            int numElements = allocatedType.arraySize; // 获取数组大小
            return elementSize * numElements;
        }
        return 4; // 默认返回4字节
    }
}
