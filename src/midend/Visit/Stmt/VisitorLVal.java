package midend.Visit.Stmt;

import frontend.Error;
import frontend.Parser.Exp.Exp;
import frontend.Parser.Stmt.LVal;
import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.GepInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.value.IrValue;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolType;
import midend.Visit.Exp.VisitorExp;

import java.util.ArrayList;

public class VisitorLVal {
    public static void VisitLVal(LVal lVal, boolean isLeftSide) {
        if (lVal == null) {
            return;
        }
        String ident = lVal.getIdent();

        Symbol symbol = GlobalSymbolTable.searchSymbolByIdent(ident, lVal.GetLineNumber());
        //LVal 为常量时，不能对其修改。
        if (symbol != null) {
            if (isLeftSide && (symbol.GetSymbolType().equals(SymbolType.CONST_INT)
                    || symbol.GetSymbolType().equals(SymbolType.CONST_INT_ARRAY))) {

                Error error = new Error(Error.ErrorType.h, lVal.GetLineNumber(), "h");
                error.printToError(error);
            }
        } else {
            //变量未定义
            Error error = new Error(Error.ErrorType.c, lVal.GetLineNumber(), "c");
            error.printToError(error);
        }

        //处理下标表达式
        if (lVal.hasIndexExp()) {
            VisitorExp.VisitExp(lVal.getIndexExp());
        }

    }

    public static void VisitLVals(ArrayList<LVal> lVals, boolean isLeftSide) {
        if (lVals == null || lVals.isEmpty()) {
            return;
        }
        for (LVal lVal : lVals) {
            VisitLVal(lVal, isLeftSide);
        }
    }

    public static IrValue LLVMVisitLVal(LVal lVal, boolean isLeftSide) {
        if (lVal == null) {
            return null;
        }
        String Ident = lVal.getIdent();
        Exp exp = lVal.getIndexExp();
        Symbol symbol = GlobalSymbolTable.searchSymbolByIdent(Ident, lVal.GetLineNumber());
        if (symbol == null) {
            System.out.println("LLVMVisitLVal: " + Ident);
            return null;
        }
        if (isLeftSide) {
            //左值
            if (!symbol.isArray()) {
                return symbol.getIrValue();
            } else {
                IrValue irValue = symbol.getIrValue();
                IrPointer pointerType = (IrPointer) irValue.irType;
                if (pointerType.targetType.isPointerType()) {
                    irValue = IrBuilder.GetNewLoadInstr(irValue);
                }
                return IrBuilder.GetNewGepInstr(irValue, VisitorExp.LLVMVisitExp(exp));
            }
        } else {
            //右值
            if (!symbol.isArray()) {
                return IrBuilder.GetNewLoadInstr(symbol.getIrValue());
            } else {
                IrValue irValue = symbol.getIrValue();
                IrPointer pointerType = (IrPointer) irValue.irType;
                if (pointerType.targetType.isPointerType()) {
                    irValue = IrBuilder.GetNewLoadInstr(irValue);
                }

                //数组下标为空时，表示指针
                if (exp == null) {
                    return IrBuilder.GetNewGepInstr(irValue, new IrConstInt(0));
                }

                //计算偏移
                IrValue irExp = VisitorExp.LLVMVisitExp(exp);
                //偏移非空时，表示数组元素
                //获取元素地址
                GepInstr gepInstr = IrBuilder.GetNewGepInstr(irValue, irExp);
                //加载元素值
                return IrBuilder.GetNewLoadInstr(gepInstr);
            }
        }
    }
}
