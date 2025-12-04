package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;

public class JumpInstr extends Instruction {
    private IrBasicBlock targetBlock;

    public JumpInstr(IrBasicBlock targetBlock) {
        super(ValueType.JUMP_INST, IrType.VOID, "jump", InstructionType.JUMP);
        this.targetBlock = targetBlock;
        this.AddUseValue(targetBlock);
    }

    public JumpInstr(IrBasicBlock targetBlock, IrBasicBlock currentBlock) {
        super(ValueType.JUMP_INST, IrType.VOID, "jump", InstructionType.JUMP);
        this.targetBlock = targetBlock;
        this.AddUseValue(targetBlock);
        currentBlock.addInstruction(this);
    }

    public IrBasicBlock getTargetBlock() {
        return targetBlock;
    }

    public String toString() {
        return "br label %" + targetBlock.irName;
    }

    @Override
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        if (this.targetBlock == oldVal) {
            this.targetBlock = (IrBasicBlock) newVal;
        }
    }

    public void setTargetBlock(IrBasicBlock clonedBlock) {
        this.targetBlock = clonedBlock;
        this.getUseValues().set(0, clonedBlock);
    }
}
