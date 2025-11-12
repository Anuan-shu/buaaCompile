package midend.Visit.Func;

import frontend.Parser.FuncDef.FuncDef;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolTable;

import java.util.ArrayList;

public class VisitorFuncDef {

    public static void VisitFuncDef(FuncDef funcDef) {
        //函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // b g

        boolean isHasReturn = !funcDef.getFuncTypeIsVoid();
        boolean inLastOutOfFunc=true;

        //作用域加一
        SymbolTable newFuncSymbolTable = new SymbolTable(GlobalSymbolTable.addScopeDepth(),
                                                        GlobalSymbolTable.getLocalSymbolTable());
        GlobalSymbolTable.getLocalSymbolTable().AddSonTable(newFuncSymbolTable);
        GlobalSymbolTable.setLocalSymbolTable(newFuncSymbolTable);

        //FuncFParams
        ArrayList<Symbol> funcParamList = new ArrayList<>();
        if(funcDef.HasFuncFParams()){
            funcParamList=VisitorFuncFParams.VisitFuncFParams(funcDef.GetFuncFParams());
        }

        //回到上一级作用域
        GlobalSymbolTable.setLocalSymbolTable(GlobalSymbolTable.getLocalSymbolTable().getFatherTable());
        GlobalSymbolTable.addFuncDef(funcDef,funcParamList);//当前函数定义加入符号表
        //进入函数作用域
        GlobalSymbolTable.setLocalSymbolTable(newFuncSymbolTable);

        //Block
        VisitorFuncBlock.VisitFuncBlock(funcDef.GetFuncBlock(),isHasReturn,inLastOutOfFunc);

        //回到上一级作用域
        GlobalSymbolTable.setLocalSymbolTable(GlobalSymbolTable.getLocalSymbolTable().getFatherTable());

    }
}
