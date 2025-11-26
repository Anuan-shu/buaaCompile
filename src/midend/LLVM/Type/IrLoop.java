package midend.LLVM.Type;

import midend.LLVM.value.IrBasicBlock;

public class IrLoop {
    private IrBasicBlock condBlock;
    private IrBasicBlock bodyBlock;
    private IrBasicBlock stepBlock;
    private IrBasicBlock afterBlock;
    public IrLoop(IrBasicBlock condBlock, IrBasicBlock bodyBlock, IrBasicBlock stepBlock, IrBasicBlock afterBlock) {
        this.condBlock = condBlock;
        this.bodyBlock = bodyBlock;
        this.stepBlock = stepBlock;
        this.afterBlock = afterBlock;
    }

    public IrBasicBlock getCondBlock() {
        return condBlock;
    }

    public IrBasicBlock getBodyBlock() {
        return bodyBlock;
    }

    public IrBasicBlock getStepBlock() {
        return stepBlock;
    }

    public IrBasicBlock getAfterBlock() {
        return afterBlock;
    }
}
