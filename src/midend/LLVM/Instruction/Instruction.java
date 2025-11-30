package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.use.IrUser;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;

public class Instruction extends IrUser {
    private final InstructionType instrType;
    private IrBasicBlock inBasicBlock;

    public Instruction(ValueType valueType, IrType irType, String irName, InstructionType instrType) {
        super(valueType, irType, irName);
        this.instrType = instrType;
    }

    public InstructionType getInstrType() {
        return instrType;
    }

    public void setParentBasicBlock(IrBasicBlock currentBasicBlock) {
        this.inBasicBlock = currentBasicBlock;
    }

    public IrBasicBlock getParent() {
        return inBasicBlock;
    }

    /**
     * 将当前指令的所有使用者中的“我”，替换为“newVal”
     */
    public void replaceAllUsesWith(IrValue newVal) {
        // 获取所属函数
        IrFunction func = (IrFunction) this.getParent().getParent();

        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction user : bb.getInstructions()) {
                user.replaceUse(this, newVal);
            }
        }
    }

    // 如果没有重写，默认什么都不做
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        System.err.println("Warning: replaceUse not implemented for " + this.getClass().getSimpleName());
    }
}
