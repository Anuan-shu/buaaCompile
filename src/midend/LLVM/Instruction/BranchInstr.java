package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;

public class BranchInstr extends Instruction {
    public BranchInstr(IrValue condition, IrBasicBlock trueBlock, IrBasicBlock falseBlock) {
        super(ValueType.BRANCH_INST, IrType.VOID, IrBuilder.GetLocalVarName(), InstructionType.BRANCH);
        this.AddUseValue(condition);
        this.AddUseValue(trueBlock);
        this.AddUseValue(falseBlock);
    }

    public String toString() {
        IrValue condition = this.getUseValues().get(0);
        IrBasicBlock trueBlock = (IrBasicBlock) this.getUseValues().get(1);
        IrBasicBlock falseBlock = (IrBasicBlock) this.getUseValues().get(2);
        return "br i1 " + condition.irName + ", label %" + trueBlock.irName + ", label %" + falseBlock.irName;
    }


    public IrValue getCond() {
        return this.getUseValues().get(0);
    }

    public IrBasicBlock getTrueBlock() {
        return (IrBasicBlock) this.getUseValues().get(1);
    }

    public IrBasicBlock getFalseBlock() {
        return (IrBasicBlock) this.getUseValues().get(2);
    }

    @Override
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        if (this.getUseValues().get(0) == oldVal) {
            this.getUseValues().set(0, newVal);
        }
    }
}
