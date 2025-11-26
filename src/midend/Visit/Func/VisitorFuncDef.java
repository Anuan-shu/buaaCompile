package midend.Visit.Func;

import frontend.Parser.FuncDef.FuncDef;
import frontend.Parser.MainFuncDef.Block;
import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.AllocateInstruction;
import midend.LLVM.Instruction.ReturnInstr;
import midend.LLVM.Instruction.StoreInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrParameter;
import midend.LLVM.value.IrValue;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolTable;

import java.util.ArrayList;

public class VisitorFuncDef {

    public static void VisitFuncDef(FuncDef funcDef) {
        //函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // b g

        boolean isHasReturn = !funcDef.getFuncTypeIsVoid();
        boolean inLastOutOfFunc = true;

        //作用域加一
        SymbolTable newFuncSymbolTable = new SymbolTable(GlobalSymbolTable.addScopeDepth(), GlobalSymbolTable.getLocalSymbolTable());
        GlobalSymbolTable.getLocalSymbolTable().AddSonTable(newFuncSymbolTable);
        GlobalSymbolTable.setLocalSymbolTable(newFuncSymbolTable);

        //FuncFParams
        ArrayList<Symbol> funcParamList = new ArrayList<>();
        if (funcDef.HasFuncFParams()) {
            funcParamList = VisitorFuncFParams.VisitFuncFParams(funcDef.GetFuncFParams());
        }

        //回到上一级作用域
        GlobalSymbolTable.setLocalSymbolTable(GlobalSymbolTable.getLocalSymbolTable().getFatherTable());
        GlobalSymbolTable.addFuncDef(funcDef, funcParamList);//当前函数定义加入符号表
        //进入函数作用域
        GlobalSymbolTable.setLocalSymbolTable(newFuncSymbolTable);

        //Block
        VisitorFuncBlock.VisitFuncBlock(funcDef.GetFuncBlock(), isHasReturn, inLastOutOfFunc);

        //回到上一级作用域
        GlobalSymbolTable.setLocalSymbolTable(GlobalSymbolTable.getLocalSymbolTable().getFatherTable());

    }

    public static void LLVMVisitFuncDef(FuncDef funcDef) {
        String funcName = funcDef.GetIdent();
        Symbol funcSymbol = GlobalSymbolTable.searchSymbolByIdent(funcName);
        boolean isHasReturn = !funcDef.getFuncTypeIsVoid();
        IrFunction function;
        if (isHasReturn) {
            function = IrBuilder.GetNewIrFunction(funcName, ValueType.FUNCTION, IrType.INT32);
        } else {
            function = IrBuilder.GetNewIrFunction(funcName, ValueType.FUNCTION, IrType.VOID);
        }
        funcSymbol.setIrValue(function);

        //作用域加一
        GlobalSymbolTable.enterSonSymbolTable();
        //处理参数
        ArrayList<Symbol> funcParamList = funcSymbol.getParamList();
        ArrayList<IrParameter> irParameters = new ArrayList<>();
        if (funcDef.HasFuncFParams()) {
            for (int i = 0; i < funcParamList.size(); i++) {
                Symbol paramSymbol = funcParamList.get(i);
                IrParameter irParameter = new IrParameter(ValueType.PARAMETER, GetParamIrType(paramSymbol), IrBuilder.GetLocalVarName());

                function.addParameter(irParameter);

                irParameters.add(irParameter);
            }

            for (int i = 0; i < irParameters.size(); i++) {
                IrParameter irParameter = irParameters.get(i);
                Symbol paramSymbol = funcParamList.get(i);
                //为参数分配空间并store
                AllocateInstruction allocInstr = IrBuilder.GetNewAllocInstrByType(irParameter.irType);
                //AllocateInstruction allocateInstruction = IrBuilder.GetNewAllocateInstruction(paramSymbol);
                StoreInstr storeInstr = IrBuilder.GetNewStoreInstrByValueAndAddress(irParameter, allocInstr);
                paramSymbol.setIrValue(allocInstr);
            }
        }
        //处理函数体
        Block funcBlock = funcDef.GetFuncBlock();
        VisitorFuncBlock.LLVMVisitFuncBlock(funcBlock);

        //无返回语句
        boolean lastIsReturn = IrBuilder.getCurrentBasicBlock().isLastInstrReturn();
        if (isHasReturn && !lastIsReturn) {
            //需要返回值但无返回语句，补充return 0
            ReturnInstr returnInstr = IrBuilder.GetNewReturnInstr(new IrConstInt(0));
        } else if (!isHasReturn && !lastIsReturn) {
            //无返回值函数，补充return
            ReturnInstr returnInstr = IrBuilder.GetNewReturnInstr(null);
        }

        //回到父级作用域
        GlobalSymbolTable.GoToFatherSymbolTable();
    }

    private static IrType GetParamIrType(Symbol paramSymbol) {
        if (paramSymbol.isArray()) {
            return new IrPointer(IrType.INT32);
        } else {
            return IrType.INT32;
        }
    }
}
