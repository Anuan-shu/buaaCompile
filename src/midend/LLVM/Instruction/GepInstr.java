package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class GepInstr extends Instruction {

    public GepInstr(IrValue pointer, IrValue offset) {
        super(ValueType.GEP_INST, new IrPointer(GetTargetType(pointer)), IrBuilder.GetLocalVarName(), InstructionType.GEP);
        this.AddUseValue(pointer);
        this.AddUseValue(offset);
    }

    private static IrType GetTargetType(IrValue pointer) {
        IrType targetType = ((IrPointer) pointer.irType).targetType;
        if (targetType.isArrayType()) {
            return targetType.getElementType();
        } else if (targetType.isPointerType()) {
            return ((IrPointer) targetType).targetType;
        } else {
            return targetType;
        }
    }

    private IrType getPointType(IrValue pointer) {
        ValueType valueType = pointer.valueType;
        if (valueType.equals(ValueType.ALLOCA_INST)) {
            AllocateInstruction allocateInstruction = (AllocateInstruction) pointer;
            return allocateInstruction.getAllocatedType();
        } else if (valueType.equals(ValueType.LOAD_INST)) {
            LoadInstr loadInstr = (LoadInstr) pointer;
            return loadInstr.getPointType();
        } else {
            throw new RuntimeException("GEP pointer type error");
        }
    }

    public String toString() {
        IrValue pointer = this.getUseValues().get(0);
        IrValue offset = this.getUseValues().get(1);
        IrPointer pointerType = (IrPointer) pointer.irType;
        IrType ptrType = pointerType.targetType;
        if (ptrType.isArrayType()) {
            return this.irName + " = getelementptr inbounds " + ptrType + ", " + pointerType + " " + pointer.irName + ", i32 0, " + offset.irType + " " + offset.irName;
        } else {
            return this.irName + " = getelementptr inbounds " + ptrType + ", " + pointerType + " " + pointer.irName + ", " + offset.irType + " " + offset.irName;
        }
    }

    public IrValue getPtr() {
        return this.getUseValues().get(0);
    }

    public IrValue getIndice() {
        return this.getUseValues().get(1);
    }
}
