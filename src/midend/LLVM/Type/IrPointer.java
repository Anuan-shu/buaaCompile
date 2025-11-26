package midend.LLVM.Type;

public class IrPointer extends IrType {
    public IrType targetType;

    public IrPointer(IrType targetType) {
        super("pointer");
        this.targetType = targetType;
    }

    @Override
    public String toString() {
        return targetType.toString() + "*";
    }
}
