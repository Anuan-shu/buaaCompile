package midend.Visit.Exp;

import frontend.Parser.Exp.PrimaryExp;
import midend.Visit.Stmt.VisitorLVal;

public class VisitorPrimaryExp {
    public static void VisitPrimaryExp(PrimaryExp primaryExp) {
        // PrimaryExp → '(' Exp ')' | LVal | Number
        if(primaryExp == null) {
            return;
        }
        if (primaryExp.getChildren().size() == 3) {
            // '(' Exp ')'
            VisitorExp.VisitExp(primaryExp.GetChildAsExp());
        } else if (primaryExp.getChildren().size() == 1) {
            // LVal | Number
            if (primaryExp.IsChildLVal()) {
                VisitorLVal.VisitLVal(primaryExp.GetChildAsLVal(),false);
            } else {
                // Number
                //处理数字字面量
            }
        }
    }
}
