package midend.Visit.Func;

import frontend.Parser.MainFuncDef.BlockItem;
import midend.Visit.Decl.VisitorDecl;
import midend.Visit.Stmt.VisitorStmt;

public class VisitorFuncBlockItem {
    public static boolean VisitFuncBlockItem(BlockItem blockItem,boolean isNeedReturn) {
        //BlockItem → Decl | Stmt

        //Decl
        if(blockItem.isDecl()){
            VisitorDecl.VisitDecl(blockItem.GetChildAsDecl(0));
            return false;
        }else {
            //Stmt
            return VisitorStmt.VisitStmt(blockItem.GetChildAsStmt(0),isNeedReturn);
        }
    }
}
