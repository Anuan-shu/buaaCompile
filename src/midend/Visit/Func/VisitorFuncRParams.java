package midend.Visit.Func;

import frontend.Error;
import frontend.Parser.Exp.Exp;
import frontend.Parser.FuncDef.FuncRParams;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolType;

public class VisitorFuncRParams {
    public static void VisitFuncRParams(FuncRParams funcRParams, Symbol funcSymbol) {
        // FuncRParams → Exp | Exp ',' FuncRParams
        if(funcRParams == null) {
            return;
        }
        int numParams = funcRParams.getChildren().size()/2 + 1;
        if(funcSymbol.getParamList().size() != numParams) {
            Error error = new Error(Error.ErrorType.d, funcRParams.getLine(),"d");
            error.printToError(error);
            return;
        }
        for (int i = 0; i < numParams; i++) {
            //处理每一个实参表达式
            Exp paramExp = funcRParams.GetExpByIndex(i);
            Symbol paramSymbol = funcSymbol.getParamList().get(i);
            SymbolType ExpType = paramExp.getExpType();
            // 传递数组给变量。
            if((paramSymbol.GetSymbolType()==SymbolType.CONST_INT_ARRAY
                    ||paramSymbol.GetSymbolType()==SymbolType.STATIC_INT_ARRAY
            ||paramSymbol.GetSymbolType()==SymbolType.INT_ARRAY)
                    && (ExpType ==SymbolType.NOT_ARRAY)){
                Error error = new Error(Error.ErrorType.e, funcRParams.getLine(),"e");
                error.printToError(error);
                return;
            }else if((paramSymbol.GetSymbolType()==SymbolType.CONST_INT
                    ||paramSymbol.GetSymbolType()==SymbolType.STATIC_INT
                    ||paramSymbol.GetSymbolType()==SymbolType.INT)
                    && (ExpType ==SymbolType.ARRAY)){
                Error error = new Error(Error.ErrorType.e, funcRParams.getLine(),"e");
                error.printToError(error);
                return;
            }
        }
    }
}
