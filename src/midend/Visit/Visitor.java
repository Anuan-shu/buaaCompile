package midend.Visit;

import frontend.Parser.ComUnit;
import frontend.Parser.Decl.Decl;
import frontend.Parser.FuncDef.FuncDef;
import midend.LLVM.IrBuilder;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrFunction;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.OutSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolType;
import midend.Visit.Decl.VisitorDecl;
import midend.Visit.Func.VisitorFuncDef;
import midend.Visit.MainFuncDef.VisitorMainFuncDef;

import java.io.FileOutputStream;

public class Visitor {
    private final ComUnit comUnit;

    public Visitor(ComUnit comUnit) {
        this.comUnit = comUnit;
    }

    public void Visit() {
        //添加库函数定义
        OutSymbolTable.addSymbol(new Symbol("getint", SymbolType.VOID_FUNC,
                0, new IrFunction(ValueType.FUNCTION, IrType.INT32, "@getint")));

        OutSymbolTable.addSymbol(new Symbol("main", SymbolType.INT_FUNC,
                0, new IrFunction(ValueType.FUNCTION, IrType.INT32, "main")));

        OutSymbolTable.addSymbol(new Symbol("printf", SymbolType.VOID_FUNC,
                0, new IrFunction(ValueType.FUNCTION, IrType.VOID, "printf")));

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

    public void llvmVisit() {
        //符号表初始化
        GlobalSymbolTable.setLocalSymbolTable(GlobalSymbolTable.getGlobalSymbolTable());
        // 遍历所有声明
        for (Decl decl : comUnit.GetDecls()) {
            VisitorDecl.LLVMVisitDecl(decl);
        }
        // 遍历所有函数定义
        for (FuncDef funcDef : comUnit.GetFuncDefs()) {
            VisitorFuncDef.LLVMVisitFuncDef(funcDef);
        }
        // 处理主函数
        VisitorMainFuncDef.LLVMVisitMainFuncDef(comUnit.GetMainFuncDef());
    }

    public void writeLLVMToFile(String file) {
        IrModule irModule = IrBuilder.getIrModule();
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(irModule.toString().getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}