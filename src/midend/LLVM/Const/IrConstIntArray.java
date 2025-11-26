package midend.LLVM.Const;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

import java.util.ArrayList;

public class IrConstIntArray extends IrConstant {
    private final ArrayList<IrConstant> array;

    public IrConstIntArray(String irName, ArrayList<IrConstant> array) {
        super(ValueType.CONST_INT_ARRAY, new IrType("array"), irName);
        this.array = array;
        this.irType.arraySize = array.size();
    }

    public ArrayList<IrConstant> getArray() {
        return array;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.irType).append(" ");
        if (this.array == null) {
            sb.append("zeroinitializer");
        } else {
            sb.append("[");

            for (int i = 0; i < array.size() - 1; i++) {
                sb.append(array.get(i).toString());
                sb.append(", ");
            }
            sb.append(array.get(array.size() - 1).toString());

            //加0
            String zero = ", i32 0";
            sb.append(zero.repeat(Math.max(0, this.irType.arraySize - array.size())));
            sb.append("]");
        }
        return sb.toString();
    }
}
