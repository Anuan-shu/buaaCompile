package frontend.Visit.Stmt;

import frontend.Error;
import frontend.Parser.Exp.Exp;
import frontend.Parser.Stmt.Stmt;
import frontend.Visit.Exp.VisitorExp;

import java.util.ArrayList;

public class VisitorPrintf {
    public static void VisitPrint(Stmt stmt) {
        String stringConst = stmt.GetStringConst();
        int numString=0;
        //%d的数量
        for(int i=0;i<stringConst.length()-1;i++){
            if(stringConst.charAt(i)=='%'&&stringConst.charAt(i+1)=='d'){
                numString++;
            }
        }
        ArrayList<Exp> exps = stmt.GetExps();
        if(numString!=exps.size()){
            Error error = new Error(Error.ErrorType.l,stmt.GetPrintLine(),"l");
            error.printToError(error);
        }
        for(Exp exp:exps){
            VisitorExp.VisitExp(exp);
        }
    }
}
