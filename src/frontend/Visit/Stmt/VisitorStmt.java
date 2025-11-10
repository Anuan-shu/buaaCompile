package frontend.Visit.Stmt;

import frontend.Parser.Stmt.Stmt;
import frontend.Visit.Exp.VisitorExp;
import frontend.Visit.MainFuncDef.VisitorBlock;

public class VisitorStmt {
    public static boolean VisitStmt(Stmt stmt,boolean isNeedReturn){
        //语句 Stmt → LVal '=' Exp ';' // h
        //| [Exp] ';'
        //| Block
        //| 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
        //| 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt // h
        //| 'break' ';' | 'continue' ';' // m
        //| 'return' [Exp] ';' // f
        //| 'printf''('StringConst {','Exp}')'';' // l
        if(stmt.isBlock()){
            VisitorBlock.VisitBlock(stmt.GetChildAsBlock(),isNeedReturn,false);
        }else if(stmt.isIfStmt()){
            VisitorStmt.VisitStmt(stmt.GetIfStmtChildAsStmt(),isNeedReturn);
            if(stmt.HasElseStmt()){
                VisitorStmt.VisitStmt(stmt.GetElseStmtChildAsStmt(),isNeedReturn);
            }
        }else if(stmt.isForStmt()){
            VisitorForStmt.VisitStmt(stmt.GetChildAsForStmt());
            VisitorStmt.VisitStmt(stmt.GetForStmtChildAsStmt(),isNeedReturn);
        }else if(stmt.isLVal()){
            VisitorLVal.VisitLVal(stmt.GetChildAsLVal(), true);

            //处理等号右边的表达式
            VisitorExp.VisitExp(stmt.GetLValRExpChildAsExp());
        }else if(stmt.isBreakContinue()){
            VisitorJump.VisitJump(stmt.isForBody(), stmt.GetJumpLineNumber());
        }
        else if(stmt.isReturn()){
            return VisitorReturn.VisitReturn(stmt,isNeedReturn);
        }else if(stmt.isPrintf()){
            VisitorPrintf.VisitPrint(stmt);
        }else if(stmt.isExp()){
            VisitorExp.VisitExp(stmt.GetExpChildAsExp());
        }
        return false;
    }
}
