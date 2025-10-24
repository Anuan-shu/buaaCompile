package frontend.Visit;
import frontend.Parser.ComUnit;
import frontend.Parser.Decl.Decl;
import frontend.Parser.FuncDef.FuncDef;
import frontend.Visit.Decl.VisitorDecl;

public class Visitor {
    private final ComUnit comUnit;

    public Visitor(ComUnit comUnit) {
        this.comUnit = comUnit;
    }

    public void Visit() {
        // 遍历所有声明
        for (Decl decl : comUnit.GetDecls()) {
            VisitorDecl.VisitDecl(decl);
        }
        // 遍历所有函数定义
        for (FuncDef funcDef : comUnit.GetFuncDefs()) {
            VisitorFuncDef.VisitFuncDef(funcDef);
        }
        // 处理主函数
        VisitorFuncDef.VisitMainFuncDef(comUnit.GetMainFuncDef());
    }
}