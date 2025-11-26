package midend.Visit.Stmt;

import frontend.Error;
import frontend.Parser.Stmt.Stmt;
import midend.LLVM.Instruction.JumpInstr;
import midend.LLVM.IrBuilder;

public class VisitorJump {
    public static void VisitJump(boolean isJump,int line) {
        if(!isJump){
            Error error = new Error(Error.ErrorType.m,line ,"m");
            error.printToError(error);
        }
    }

    public static void LLVMVisitJump(Stmt stmt) {
        if(stmt.isBreak()){
            JumpInstr jumpInstr = IrBuilder.GetNewJumpInstr(IrBuilder.LoopPeek().getAfterBlock());
        }else {
            JumpInstr jumpInstr = IrBuilder.GetNewJumpInstr(IrBuilder.LoopPeek().getStepBlock());
        }
    }
}
