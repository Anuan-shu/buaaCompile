package midend.LLVM.value;

import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.use.IrUse;

import java.util.ArrayList;

public class IrValue {
    public final ValueType valueType;
    public final IrType irType;
    public final String irName;

    public final ArrayList<IrUse> useList;

    public IrValue(ValueType valueType, IrType irType, String irName) {
        this.valueType = valueType;
        this.irType = irType;
        this.irName = irName;
        this.useList = new ArrayList<>();
    }

    public void addUse(IrUse irUse) {
        useList.add(irUse);
    }

}
