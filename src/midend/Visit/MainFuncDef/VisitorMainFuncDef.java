package midend.Visit.MainFuncDef;

import frontend.Parser.MainFuncDef.MainFuncDef;

public class VisitorMainFuncDef {
    public static void VisitMainFuncDef(MainFuncDef mainFuncDef) {
        //主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // g
        boolean needReturn = true;
        boolean inLastOutOfFunc = true;
        VisitorBlock.VisitBlock(mainFuncDef.GetBlock(),needReturn,inLastOutOfFunc);
    }
}
