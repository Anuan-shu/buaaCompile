package frontend.Visit.Stmt;

import frontend.Parser.Stmt.ForStmt;

import java.util.ArrayList;

public class VisitorForStmt {
    public static void VisitStmt(ArrayList<ForStmt> forStmts) {
        if(!forStmts.isEmpty()) {
            for (ForStmt forStmt : forStmts) {
                VisitorLVal.VisitLVals(forStmt.getLVals(),true);
            }
        }
    }
}
