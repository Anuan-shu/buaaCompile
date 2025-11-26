package midend.Visit.MainFuncDef;

import frontend.Parser.MainFuncDef.MainFuncDef;
import midend.LLVM.IrBuilder;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrFunction;
import midend.Symbol.GlobalSymbolTable;

public class VisitorMainFuncDef {
    public static void VisitMainFuncDef(MainFuncDef mainFuncDef) {
        //主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // g
        boolean needReturn = true;
        boolean inLastOutOfFunc = true;
        VisitorBlock.VisitBlock(mainFuncDef.GetBlock(),needReturn,inLastOutOfFunc);
    }

    public static void LLVMVisitMainFuncDef(MainFuncDef mainFuncDef) {
        //主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // g
        IrBuilder.GetNewIrFunction("main", ValueType.FUNCTION,IrType.INT32);

        VisitorBlock.LLVMVisitBlock(mainFuncDef.GetBlock());
    }
}
