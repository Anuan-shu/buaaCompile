package midend.Visit.MainFuncDef;

import frontend.Parser.MainFuncDef.Block;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.SymbolTable;
import midend.Visit.Func.VisitorFuncBlock;

//创建新作用域
public class VisitorBlock {
    public static void VisitBlock(Block block,boolean isNeedReturn,boolean inLastOutOfFunc) {
        //作用域加一
        SymbolTable newFuncSymbolTable = new SymbolTable(GlobalSymbolTable.addScopeDepth(),
                GlobalSymbolTable.getLocalSymbolTable());
        GlobalSymbolTable.getLocalSymbolTable().AddSonTable(newFuncSymbolTable);
        GlobalSymbolTable.setLocalSymbolTable(newFuncSymbolTable);

        VisitorFuncBlock.VisitFuncBlock(block, isNeedReturn, inLastOutOfFunc);
        //回到上一级作用域
        GlobalSymbolTable.setLocalSymbolTable(GlobalSymbolTable.getLocalSymbolTable().getFatherTable());
    }
}
