package midend.Visit.Func;

import frontend.Parser.FuncDef.FuncFParam;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolType;

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
