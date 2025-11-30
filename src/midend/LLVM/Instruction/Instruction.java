package midend.LLVM.Instruction;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.use.IrUser;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

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

    // 判断指令是否必须固定在当前块（有副作用或控制流）
    public boolean isPinned() {
        return this instanceof BranchInstr ||
                this instanceof JumpInstr ||
                this instanceof ReturnInstr ||
                this instanceof StoreInstr ||
                this instanceof LoadInstr || // 必须保留 Load，除非做了别名分析
                this instanceof CallInstr ||
                this instanceof PhiInstr ||  // Phi 必须在块头，不能动
                this instanceof PrintIntInstr ||
                this instanceof PrintStrInstr ||
                this instanceof AllocateInstruction;
    }

    // 判断指令是否满足交换律 (a + b == b + a)
    public boolean isCommutative() {
        if (this instanceof AluInst) {
            String op = ((AluInst) this).getOp();
            return op.equals("ADD") || op.equals("MUL") || op.equals("AND") || op.equals("OR") || op.equals("XOR");
        }
        if (this instanceof CmpInstr) {
            String op = ((CmpInstr) this).getOp();
            return op.equals("EQ") || op.equals("NE");
        }
        return false;
    }

    public boolean isTerminator() {
        return this instanceof BranchInstr || this instanceof JumpInstr || this instanceof ReturnInstr;
    }
}
