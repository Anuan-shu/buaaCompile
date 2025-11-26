package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.use.IrUser;
import midend.LLVM.value.IrBasicBlock;

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
}
