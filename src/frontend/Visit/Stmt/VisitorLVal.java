package frontend.Visit.Stmt;

import frontend.Error;
import frontend.Parser.Stmt.LVal;
import frontend.Symbol.GlobalSymbolTable;
import frontend.Symbol.Symbol;
import frontend.Symbol.SymbolType;
import frontend.Visit.Exp.VisitorExp;

import java.util.ArrayList;

public class VisitorLVal {
    public static void VisitLVal(LVal lVal,boolean isLeftSide) {
        if(lVal == null) {
            return;
        }
        String ident = lVal.getIdent();

        Symbol symbol = GlobalSymbolTable.searchSymbolByIdent(ident);
        //LVal 为常量时，不能对其修改。
        if(symbol != null){
            if(isLeftSide&&(symbol.GetSymbolType().equals(SymbolType.CONST_INT)
                    ||symbol.GetSymbolType().equals(SymbolType.CONST_INT_ARRAY))){
                Error error = new Error(Error.ErrorType.h, lVal.GetLineNumber(),"h");
                error.printToError(error);
            }
        }else{
            //变量未定义
            Error error = new Error(Error.ErrorType.c, lVal.GetLineNumber(),"c");
            error.printToError(error);
        }

        //处理下标表达式
        if(lVal.hasIndexExp()){
            VisitorExp.VisitExp(lVal.getIndexExp());
        }

    }
    public static void VisitLVals(ArrayList<LVal> lVals, boolean isLeftSide) {
        if(lVals == null || lVals.isEmpty()) {
            return;
        }
        for (LVal lVal : lVals) {
            VisitLVal(lVal,isLeftSide);
        }
    }
}
