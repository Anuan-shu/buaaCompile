package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class PrintIntInstr extends Instruction {
    public PrintIntInstr(IrValue printValue) {
        super(ValueType.PRINT_INST, IrType.VOID, IrBuilder.GetLocalVarName(), InstructionType.PRINT);
        this.AddUseValue(printValue);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        IrValue printValue = this.getUseValues().get(0);
        return "call void @putint(i32 " + printValue.irName + ")";
    }

    public IrValue getPrintValue() {
        return this.getUseValues().get(0);
    }
}
