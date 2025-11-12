package midend.Visit.Exp;

import frontend.Error;
import frontend.Parser.Exp.UnaryExp;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Visit.Func.VisitorFuncRParams;

public class VisitorUnaryExp {
    public static void VisitUnaryExp(UnaryExp unaryExp) {
        // UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
        if(unaryExp == null) {
            return;
        }
        if (unaryExp.getChildren().size() == 1) {
            // PrimaryExp
            VisitorPrimaryExp.VisitPrimaryExp(unaryExp.GetFirstChildAsPrimaryExp());
        } else if (unaryExp.getChildren().size() == 3 || unaryExp.getChildren().size() == 4) {
            // Ident '(' [FuncRParams] ')'
            //Ident
            String funcName = unaryExp.GetChildAsFuncName();
            Symbol funcSymbol = GlobalSymbolTable.searchSymbolByIdent(funcName);
            if(funcSymbol == null ) {
                //函数未定义
                Error error = new Error(Error.ErrorType.c, unaryExp.GetFuncNameLine(),"c");
                error.printToError(error);
                return;
            }
            //处理函数调用
            //处理实参列表
            if (unaryExp.getChildren().size() == 4) {
                //有实参列表
                VisitorFuncRParams.VisitFuncRParams(unaryExp.GetChildAsFuncRParams(), funcSymbol);
            }else if(!funcSymbol.getParamList().isEmpty()) {
                //无实参列表但函数有形参
                Error error = new Error(Error.ErrorType.d, unaryExp.GetFuncNameLine(),"d");
                error.printToError(error);
                return;
            }
        } else if (unaryExp.getChildren().size() == 2) {
            // UnaryOp UnaryExp
            //处理一元运算符
            //处理下一个一元表达式
            VisitorUnaryExp.VisitUnaryExp(unaryExp.GetChildAsUnaryExpByIndex(1));
        }
    }
}
