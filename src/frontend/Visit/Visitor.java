package frontend.Visit;
import frontend.Parser.ComUnit;
import frontend.Parser.Decl.Decl;
import frontend.Parser.FuncDef.FuncDef;
import frontend.Symbol.OutSymbolTable;
import frontend.Symbol.Symbol;
import frontend.Symbol.SymbolType;
import frontend.Visit.Decl.VisitorDecl;
import frontend.Visit.Func.VisitorFuncDef;
import frontend.Visit.MainFuncDef.VisitorMainFuncDef;

public class Visitor {
    private final ComUnit comUnit;

    public Visitor(ComUnit comUnit) {
        this.comUnit = comUnit;
    }

    public void Visit() {
        //添加库函数定义
        OutSymbolTable.addSymbol(new Symbol("getint", SymbolType.VOID_FUNC,0));
        OutSymbolTable.addSymbol(new Symbol("main", SymbolType.INT_FUNC,0));
        OutSymbolTable.addSymbol(new Symbol("printf", SymbolType.VOID_FUNC,0));

        // 遍历所有声明
        for (Decl decl : comUnit.GetDecls()) {
            VisitorDecl.VisitDecl(decl);
        }
        // 遍历所有函数定义
        for (FuncDef funcDef : comUnit.GetFuncDefs()) {
            VisitorFuncDef.VisitFuncDef(funcDef);
        }
        // 处理主函数
        VisitorMainFuncDef.VisitMainFuncDef(comUnit.GetMainFuncDef());
    }
}