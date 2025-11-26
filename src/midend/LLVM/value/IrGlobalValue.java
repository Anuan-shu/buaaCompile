package midend.LLVM.value;

import midend.LLVM.Const.IrConstant;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

public class IrGlobalValue extends IrValue{
    private final IrConstant initial;
    private boolean isStatic=false;
    public IrGlobalValue(ValueType valueType, IrType irType, String irName, IrConstant initial) {
        super(valueType,irType, irName);
        this.initial = initial;
    }
    public IrGlobalValue(ValueType valueType, IrType irType, String irName, IrConstant initial, boolean isStatic) {
        super(valueType,irType, irName);
        this.initial = initial;
        this.isStatic=isStatic;
    }
    public IrConstant getInitial() {
        return initial;
    }

    public String toString() {
        if(isStatic){
            return irName + " = internal global " + this.initial;
        }else{
            return this.irName + " = dso_local global " + this.initial;
        }
    }
}
