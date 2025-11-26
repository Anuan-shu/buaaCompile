package midend.LLVM.Type;

import midend.LLVM.Instruction.ZextInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.value.IrValue;

public class IrType {
    public static final IrType MODULE = new IrType("module");
    public static final IrType FUNCTION = new IrType("function");
    public static final IrType POINTER = new IrType("pointer");
    public static final IrType BASICBLOCK = new IrType("basicblock");
    public static final IrType VOID = new IrType("void");
    public static final IrType INT1 = new IrType("i1");
    public static final IrType INT8 = new IrType("i8");
    public static final IrType INT32 = new IrType("i32");
    public static final IrType ARRAY = new IrType("array");//array元素默认int32
    public static final IrType STRING = new IrType("string");//string元素默认int8

    private final String typeName;
    public int arraySize = -1; // -1表示非数组类型

    public IrType(String typeName) {
        this.typeName = typeName;
    }

    public static IrValue convertValueToType(IrValue value, IrType targetType) {
        if (targetType == IrType.INT32) {
            if (value.irType == IrType.INT32) {
                return value;
            } else {
                return IrBuilder.GetNewZextInstr(value, targetType);
            }
        } else if (targetType == IrType.INT8) {
            if (value.irType == IrType.INT8) {
                return value;
            } else if (value.irType == IrType.INT32) {
                return IrBuilder.GetNewTruncInstr(value, targetType);
            } else {
                return IrBuilder.GetNewZextInstr(value, targetType);
            }
        } else if (targetType == IrType.INT1) {
            if (value.irType == IrType.INT1) {
                return value;
            } else {
                return IrBuilder.GetNewTruncInstr(value, targetType);
            }
        }
        return value;
    }

    public boolean isArrayType() {
        return this.typeName.equals("array");
    }

    public String toString() {
        if (this.typeName.equals("array")) {
            return "[" + this.arraySize + " x i32]";
        } else if (this.typeName.equals("string")) {
            return "[" + this.arraySize + " x i8]";
        }
        return typeName;
    }

    public IrType getElementType() {
        if (this.typeName.equals("array")) {
            return IrType.INT32;
        }
        throw new RuntimeException("Not an array type");
    }

    public boolean isPointerType() {
        return this.typeName.equals("pointer");
    }
}
