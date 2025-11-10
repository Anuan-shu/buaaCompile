package frontend.Visit.Stmt;

import frontend.Error;
import frontend.Parser.Stmt.Stmt;
import frontend.Visit.Exp.VisitorExp;

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
}
