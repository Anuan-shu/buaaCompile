package frontend.Visit.Func;

import frontend.Parser.FuncDef.FuncFParam;
import frontend.Symbol.GlobalSymbolTable;
import frontend.Symbol.Symbol;
import frontend.Symbol.SymbolType;

public class VisitorFuncFParam {
    public static Symbol VisitFuncFParam(FuncFParam funcFParam){
        //函数形参 FuncFParam → BType Ident ['[' ']']
        //加入符号表
        //获取参数名
        String paramName = funcFParam.GetIdentName();
        //获取参数类型
        SymbolType paramType = funcFParam.GetSymbolType();
        int line = funcFParam.GetLineNumber();
        //加入符号表
        Symbol paramSymbol = new Symbol(paramName, paramType,line);
        GlobalSymbolTable.getLocalSymbolTable().AddSymbol(paramSymbol);
        return paramSymbol;
    }
}
