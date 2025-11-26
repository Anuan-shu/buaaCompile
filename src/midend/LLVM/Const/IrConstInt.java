package midend.LLVM.Const;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

public class IrConstInt extends IrConstant {
    private final int value;

    public IrConstInt(int value) {
        super(ValueType.CONSTANT, IrType.INT32, String.valueOf(value));
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public String toString() {
        return "i32 " + value;
    }
}
