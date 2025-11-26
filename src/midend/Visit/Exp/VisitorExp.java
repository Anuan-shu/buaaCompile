package midend.Visit.Exp;

import frontend.Parser.Exp.Exp;
import midend.LLVM.value.IrValue;

public class VisitorExp {
    public static void VisitExp(Exp exp) {
        if(exp!=null) {
            VisitorAddExp.VisitAddExp(exp.GetChildAsAddExp());
        }
    }

    public static IrValue LLVMVisitExp(Exp exp) {
        if(exp!=null) {
            return VisitorAddExp.LLVMVisitAddExp(exp.GetChildAsAddExp());
        }
        return null;
    }
}
