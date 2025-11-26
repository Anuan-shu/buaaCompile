package midend.LLVM.use;

import midend.LLVM.value.IrValue;

public class IrUse {
    private final IrUser user;
    private final IrValue value;
    public IrUse(IrUser user, IrValue value) {
        this.user = user;
        this.value = value;
    }
    public IrUser GetUser(){
        return user;
    }
    public IrValue GetValue(){
        return value;
    }
}
