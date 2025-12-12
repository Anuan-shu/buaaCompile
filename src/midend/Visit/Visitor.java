package midend.Visit;

import frontend.Parser.ComUnit;
import frontend.Parser.Decl.Decl;
import frontend.Parser.FuncDef.FuncDef;
import midend.LLVM.IrBuilder;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrFunction;
import midend.Optimization.*;
import midend.SSA.DeadCodeElimination;
import midend.SSA.Mem2Reg;
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
    public boolean optimize = false;

    public Visitor(ComUnit comUnit) {
        this.comUnit = comUnit;
    }

    public void Visit() {
        //添加库函数定义
        OutSymbolTable.addSymbol(new Symbol("getint", SymbolType.VOID_FUNC, 0, new IrFunction(ValueType.FUNCTION, IrType.INT32, "@getint")));

        OutSymbolTable.addSymbol(new Symbol("main", SymbolType.INT_FUNC, 0, new IrFunction(ValueType.FUNCTION, IrType.INT32, "main")));

        OutSymbolTable.addSymbol(new Symbol("printf", SymbolType.VOID_FUNC, 0, new IrFunction(ValueType.FUNCTION, IrType.VOID, "printf")));

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

    public void llvmVisit(boolean optimize) {
        this.optimize = optimize;
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

        if (optimize) {
            // 1. 基础优化
            FunctionInlining functionInlining = new FunctionInlining();
            functionInlining.run(IrBuilder.getIrModule());

            BlockMerge blockMerge = new BlockMerge();
            blockMerge.run(IrBuilder.getIrModule());

            // 先清理掉不可达块，防止 Mem2Reg 处理它们
            RemoveUnreachableBlocks removeUnreachable = new RemoveUnreachableBlocks();
            removeUnreachable.run(IrBuilder.getIrModule());

            // 2. 全局变量局部化
            GlobalVariableLocalization globalLoc = new GlobalVariableLocalization();
            globalLoc.run(IrBuilder.getIrModule());

            // 3. 第一轮 Mem2Reg (处理普通的局部变量)
            Mem2Reg mem2Reg = new Mem2Reg();
            mem2Reg.run(IrBuilder.getIrModule());

            // 中间清理
            GlobalValueNumbering gvn = new GlobalValueNumbering();
            gvn.run(IrBuilder.getIrModule());

            DeadCodeElimination dce = new DeadCodeElimination();
            dce.run(IrBuilder.getIrModule());

            // 4. 循环展开
            SimpleLoopUnroll loopUnroll = new SimpleLoopUnroll();
            loopUnroll.run(IrBuilder.getIrModule());

            // 5. SROA
            // 数组被拆解为标量
            SROA sroa = new SROA();
            sroa.run(IrBuilder.getIrModule());

            // 6. 第二轮 Mem2Reg
            // 将拆解后的标量提升为寄存器 (Phi)
            mem2Reg.run(IrBuilder.getIrModule());

            // 7. 后续优化
            gvn.run(IrBuilder.getIrModule());
            LICM licm = new LICM();
            licm.run(IrBuilder.getIrModule());
            GlobalCodeMotion gcm = new GlobalCodeMotion();
            gcm.run(IrBuilder.getIrModule());

            SimpleConstProp constProp = new SimpleConstProp();
            constProp.run(IrBuilder.getIrModule());

            dce.run(IrBuilder.getIrModule());
            // 最后再清理一次，消除优化产生的死块
            removeUnreachable.run(IrBuilder.getIrModule());
            blockMerge.run(IrBuilder.getIrModule());
        }
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