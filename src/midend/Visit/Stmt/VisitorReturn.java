package midend.Visit.Stmt;

import frontend.Error;
import frontend.Parser.Stmt.Stmt;
import midend.LLVM.Instruction.ReturnInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.value.IrValue;
import midend.Visit.Exp.VisitorExp;

public class VisitorReturn {
    public static boolean VisitReturn(Stmt stmt, boolean isNeedReturn) {
        boolean returnHasValue = stmt.ReturnHasValue();
        if (returnHasValue) {
            if(!isNeedReturn) {
                Error error = new Error(Error.ErrorType.f, stmt.GetReturnLine(),"f");
                error.printToError(error);
            }
            VisitorExp.VisitExp(stmt.GetReturnExp());
        }
        return returnHasValue;
    }

    public static void LLVMVisitReturn(Stmt stmt) {
        boolean returnHasValue = stmt.ReturnHasValue();
        IrValue returnValue = null;
        if (returnHasValue) {
            returnValue = VisitorExp.LLVMVisitExp(stmt.GetReturnExp());
        }
        ReturnInstr returnInstr = IrBuilder.GetNewReturnInstr(returnValue);
    }
}
