package midend.LLVM.use;

import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrValue;
import midend.LLVM.ValueType;

import java.util.ArrayList;

public class IrUser extends IrValue {
    private final ArrayList<IrValue> useValues;
    public IrUser(ValueType valueType, IrType irType, String irName) {
        super(valueType, irType, irName);
        this.useValues = new ArrayList<>();
    }

    public ArrayList<IrValue> getUseValues() {
        return useValues;
    }

    protected void AddUseValue(IrValue valueValue) {
        useValues.add(valueValue);
        if(valueValue!=null){
            valueValue.addUse(new IrUse(this,valueValue));
        }
    }
}
