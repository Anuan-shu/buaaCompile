package midend.LLVM.Const;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

public class IrConstant extends IrValue {
    public IrConstant(ValueType valueType, IrType irType, String irName) {
        super(valueType, irType, irName);
    }
}
