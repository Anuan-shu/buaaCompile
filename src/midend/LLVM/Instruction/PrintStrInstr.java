package midend.LLVM.Instruction;

import midend.LLVM.Const.IrConstString;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

public class PrintStrInstr extends Instruction {
    private IrConstString formatString;

    public PrintStrInstr(IrConstString formatString) {
        super(ValueType.PRINT_INST, IrType.VOID, IrBuilder.GetLocalVarName(), InstructionType.PRINT);
        this.formatString = formatString;
    }


    public String toString() {
        IrPointer pointerType = (IrPointer) this.formatString.irType;
        return "call void @putstr(i8* getelementptr inbounds (" + pointerType.targetType + ", " + pointerType + " " + this.formatString.irName + ", i64 0, i64 0))";
    }
}
