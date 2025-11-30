package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrValue;

//store i32 %2, i32* %1
public class StoreInstr extends Instruction {
    public StoreInstr(IrValue valueValue, IrValue addressValue) {
        super(ValueType.STORE_INST, IrType.VOID, "store", InstructionType.STORE);
        this.AddUseValue(valueValue);
        this.AddUseValue(addressValue);
    }

    public String toString() {
        IrValue value = this.getUseValues().get(0);
        IrValue address = this.getUseValues().get(1);
        return "store " + value.irType.toString() + " " + value.irName + ", " + address.irType.toString() + " " + address.irName;
    }

    public IrValue getVal() {
        return this.getUseValues().get(0);
    }

    public IrValue getPtr() {
        return this.getUseValues().get(1);
    }


    @Override
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        // Store 有两个操作数：val (要存的值) 和 ptr (地址)
        // 通常只替换 val，ptr 如果被替换说明 ptr 本身是 Load 出来的（多级指针），也要处理
        if (this.getUseValues().get(0) == oldVal) {
            this.getUseValues().set(0, newVal);
        }
        if (this.getUseValues().get(1) == oldVal) {
            this.getUseValues().set(1, newVal);
        }
    }
}
