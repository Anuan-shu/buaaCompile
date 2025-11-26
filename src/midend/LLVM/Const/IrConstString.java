package midend.LLVM.Const;

import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

public class IrConstString extends IrConstant {
    private String str;

    public IrConstString(String str, String name) {
        super(ValueType.CONST_INT_ARRAY, new IrPointer(new IrType("string")), name);
        this.str = str;
        ((IrPointer) this.irType).targetType.arraySize = GetStringLength(str);
    }

    private static int GetStringLength(String string) {
        int length = 0;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) != '\\') {
                length++;
            } else {
                if (i != string.length() - 1 && string.charAt(i + 1) == 'n') {
                    i++;
                    length++;
                }
            }
        }
        return length + 1;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.irName);
        builder.append(" = constant ");
        builder.append(((IrPointer) this.irType).targetType);
        // 拼接字符串
        builder.append(" c\"");
        builder.append(this.str.replaceAll("\\\\n", "\\\\0A"));
        builder.append("\\00\"");
        return builder.toString();
    }
}
