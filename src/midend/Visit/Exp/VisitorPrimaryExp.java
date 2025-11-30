package midend.Visit.Exp;

import frontend.Parser.Exp.PrimaryExp;
import midend.LLVM.Const.IrConstInt;
import midend.LLVM.value.IrValue;
import midend.Visit.Stmt.VisitorLVal;

public class VisitorPrimaryExp {
    public static void VisitPrimaryExp(PrimaryExp primaryExp) {
        // PrimaryExp → '(' Exp ')' | LVal | Number
        if (primaryExp == null) {
            return;
        }
        if (primaryExp.getChildren().size() == 3) {
            // '(' Exp ')'
            VisitorExp.VisitExp(primaryExp.GetChildAsExp());
        } else if (primaryExp.getChildren().size() == 1) {
            // LVal | Number
            if (primaryExp.IsChildLVal()) {
                VisitorLVal.VisitLVal(primaryExp.GetChildAsLVal(), false);
            }
        }
    }

    public static IrValue LLVMVisitPrimaryExp(PrimaryExp primaryExp) {
        // PrimaryExp → '(' Exp ')' | LVal | Number
        if (primaryExp == null) {
            return null;
        }
        if (primaryExp.getChildren().size() == 3) {
            // '(' Exp ')'
            return VisitorExp.LLVMVisitExp(primaryExp.GetChildAsExp());
        } else if (primaryExp.getChildren().size() == 1) {
            // LVal | Number
            if (primaryExp.IsChildLVal()) {
                return VisitorLVal.LLVMVisitLVal(primaryExp.GetChildAsLVal(), false);
            } else {
                // Number
                return new IrConstInt(primaryExp.GetChildAsNumber());
            }
        }
        return null;
    }
}
