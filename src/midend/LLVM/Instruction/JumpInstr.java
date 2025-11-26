package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrBasicBlock;

public class JumpInstr extends Instruction {
    private IrBasicBlock targetBlock;

    public JumpInstr(IrBasicBlock targetBlock) {
        super(ValueType.JUMP_INST, IrType.VOID, "jump", InstructionType.JUMP);
        this.targetBlock = targetBlock;
        this.AddUseValue(targetBlock);
    }

    public IrBasicBlock getTargetBlock() {
        return targetBlock;
    }

    public String toString() {
        return "br label %" + targetBlock.irName;
    }
}
