package midend.Visit.Exp;

import frontend.Parser.Stmt.Cond;
import midend.LLVM.value.IrBasicBlock;

public class VisitorCond {
    public static void LLVMVisitCond(Cond cond, IrBasicBlock ifBlock, IrBasicBlock afterIfBlock) {
        // 处理条件表达式
        VisitorLorExp.LLVMVisitLorExp(cond.GetLOrExp(), ifBlock, afterIfBlock);
    }
}
