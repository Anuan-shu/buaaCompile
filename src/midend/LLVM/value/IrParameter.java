package midend.LLVM.value;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

public class IrParameter extends IrValue {

    public IrParameter(ValueType valueType, IrType irType, String irName) {
        super(valueType, irType, irName);
    }

    public String toString() {
        return this.irType + " " + this.irName;
    }
}
