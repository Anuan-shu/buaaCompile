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
        // 添加库函数定义
        OutSymbolTable.addSymbol(new Symbol("getint", SymbolType.VOID_FUNC, 0,
                new IrFunction(ValueType.FUNCTION, IrType.INT32, "@getint")));

        OutSymbolTable.addSymbol(
                new Symbol("main", SymbolType.INT_FUNC, 0, new IrFunction(ValueType.FUNCTION, IrType.INT32, "main")));

        OutSymbolTable.addSymbol(new Symbol("printf", SymbolType.VOID_FUNC, 0,
                new IrFunction(ValueType.FUNCTION, IrType.VOID, "printf")));

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
        // 符号表初始化
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
            // 0. 早期常量函数求值 - 在内联前求值
            ConstFunctionEval constFuncEval = new ConstFunctionEval();
            constFuncEval.run(IrBuilder.getIrModule());

            // 1. 基础内联与合并
            FunctionInlining functionInlining = new FunctionInlining();
            functionInlining.run(IrBuilder.getIrModule());

            BlockMerge blockMerge = new BlockMerge();
            blockMerge.run(IrBuilder.getIrModule());

            // 2. 第一轮 Mem2Reg - 将 alloca/load/store 转换为 SSA
            Mem2Reg mem2Reg = new Mem2Reg();
            mem2Reg.run(IrBuilder.getIrModule());

            // 3. 清理
            DeadCodeElimination dce = new DeadCodeElimination();
            dce.run(IrBuilder.getIrModule());

            // 4. 编译时常量函数求值 (必须在 Mem2Reg 之后，否则会有 store 指令)
            constFuncEval.run(IrBuilder.getIrModule());

            // 4.1 常量传播和GVN
            GlobalValueNumbering gvn = new GlobalValueNumbering();
            gvn.run(IrBuilder.getIrModule());
            SimpleConstProp simpleConstProp = new SimpleConstProp();
            simpleConstProp.run(IrBuilder.getIrModule());

            // 4.2 再次尝试常量函数求值 (处理 fib(fib(5)+2) 这样的情况)
            constFuncEval.run(IrBuilder.getIrModule());
            simpleConstProp.run(IrBuilder.getIrModule());

            // 5. 循环展开 (可能产生死代码)
            SimpleLoopUnroll simpleLoopUnroll = new SimpleLoopUnroll();
            simpleLoopUnroll.run(IrBuilder.getIrModule());

            // 在 SROA 之前清理不可达块
            RemoveUnreachableBlocks removeUnreachable = new RemoveUnreachableBlocks();
            removeUnreachable.run(IrBuilder.getIrModule());
            dce.run(IrBuilder.getIrModule());

            // 6. SROA
            SROA sroa = new SROA();
            sroa.run(IrBuilder.getIrModule());

            // 7. 第二轮 Mem2Reg
            mem2Reg.run(IrBuilder.getIrModule());

            // 8. 后续优化
            gvn.run(IrBuilder.getIrModule());
            LICM licm = new LICM();
            licm.run(IrBuilder.getIrModule());
            GlobalCodeMotion globalCodeMotion = new GlobalCodeMotion();
            globalCodeMotion.run(IrBuilder.getIrModule());

            // 额外一轮优化以发现更多机会
            simpleConstProp.run(IrBuilder.getIrModule());
            gvn.run(IrBuilder.getIrModule());
            dce.run(IrBuilder.getIrModule());

            // 循环强度削减
            LoopStrengthReduction loopSR = new LoopStrengthReduction();
            loopSR.run(IrBuilder.getIrModule());

            // 部分循环展开
            PartialLoopUnroll partialUnroll = new PartialLoopUnroll();
            partialUnroll.run(IrBuilder.getIrModule());

            simpleConstProp.run(IrBuilder.getIrModule());

            // 最终清理
            dce.run(IrBuilder.getIrModule());
            removeUnreachable.run(IrBuilder.getIrModule());
            blockMerge.run(IrBuilder.getIrModule());

            // 死函数消除 - 删除不再被调用的函数
            DeadFunctionElimination deadFuncElim = new DeadFunctionElimination();
            deadFuncElim.run(IrBuilder.getIrModule());
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