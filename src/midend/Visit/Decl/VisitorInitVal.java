package midend.Visit.Decl;

import frontend.Parser.Exp.Exp;
import frontend.Parser.Stmt.InitVal;
import midend.Visit.Exp.VisitorExp;

public class VisitorInitVal {
    public static void VisitInitVal(InitVal initVal) {
        //变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
        if(initVal==null){
            return;
        }
        if(initVal.isExp()){
            VisitorExp.VisitExp(initVal.getExp());
        } else {
            for (Exp exp : initVal.getExpList()) {
                VisitorExp.VisitExp(exp);
            }
        }
    }
}
