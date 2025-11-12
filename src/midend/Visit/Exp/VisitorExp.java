package midend.Visit.Exp;

import frontend.Parser.Exp.Exp;

public class VisitorExp {
    public static void VisitExp(Exp exp) {
        if(exp!=null) {
            VisitorAddExp.VisitAddExp(exp.GetChildAsAddExp());
        }
    }
}
