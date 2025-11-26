package midend.Visit.Exp;

import frontend.Error;
import frontend.Parser.Exp.UnaryExp;
import frontend.Parser.Exp.UnaryOp;
import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.CmpInstr;
import midend.LLVM.Instruction.ZextInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Visit.Func.VisitorFuncRParams;

public class VisitorUnaryExp {
    public static void VisitUnaryExp(UnaryExp unaryExp) {
        // UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
        if(unaryExp == null) {
            return;
        }
        if (unaryExp.getChildren().size() == 1) {
            // PrimaryExp
            VisitorPrimaryExp.VisitPrimaryExp(unaryExp.GetFirstChildAsPrimaryExp());
        } else if (unaryExp.getChildren().size() == 3 || unaryExp.getChildren().size() == 4) {
            // Ident '(' [FuncRParams] ')'
            //Ident
            String funcName = unaryExp.GetChildAsFuncName();
            Symbol funcSymbol = GlobalSymbolTable.searchSymbolByIdent(funcName);
            if(funcSymbol == null ) {
                //函数未定义
                Error error = new Error(Error.ErrorType.c, unaryExp.GetFuncNameLine(),"c");
                error.printToError(error);
                return;
            }
            //处理函数调用
            //处理实参列表
            if (unaryExp.getChildren().size() == 4) {
                //有实参列表
                VisitorFuncRParams.VisitFuncRParams(unaryExp.GetChildAsFuncRParams(), funcSymbol);
            }else if(!funcSymbol.getParamList().isEmpty()) {
                //无实参列表但函数有形参
                Error error = new Error(Error.ErrorType.d, unaryExp.GetFuncNameLine(),"d");
                error.printToError(error);
                return;
            }
        } else if (unaryExp.getChildren().size() == 2) {
            // UnaryOp UnaryExp
            //处理一元运算符
            //处理下一个一元表达式
            VisitorUnaryExp.VisitUnaryExp(unaryExp.GetChildAsUnaryExpByIndex(1));
        }
    }

    public static IrValue LLVMVisitUnaryExp(UnaryExp grandChild) {
        // UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
        if(grandChild == null) {
            return null;
        }
        if (grandChild.getChildren().size() == 1) {
            // PrimaryExp
            return VisitorPrimaryExp.LLVMVisitPrimaryExp(grandChild.GetFirstChildAsPrimaryExp());
        } else if (grandChild.getChildren().size() == 3 || grandChild.getChildren().size() == 4) {
            // Ident '(' [FuncRParams] ')'
            //处理实参列表
            String funcName = grandChild.GetChildAsFuncName();
            if (grandChild.getChildren().size() == 4) {
                //有实参列表
                return VisitorFuncRParams.LLVMVisitFuncRParams(funcName,grandChild.GetChildAsFuncRParams());
            }else{
                return VisitorFuncRParams.LLVMVisitFuncRParams(funcName);
            }
        } else if (grandChild.getChildren().size() == 2) {
            // UnaryOp UnaryExp
            //处理下一个一元表达式
            return VisitorUnaryExp.LLVMVisitOpUnaryExp(grandChild.GetOp(),grandChild.GetChildAsUnaryExpByIndex(1));
        }
        return null;
    }

    private static IrValue LLVMVisitOpUnaryExp(String op, UnaryExp unaryExp) {
        IrValue ret = VisitorUnaryExp.LLVMVisitUnaryExp(unaryExp);

        if(op.equals("+")){
            return ret;
        }else if(op.equals("-")){
            IrConstInt zero = new IrConstInt(0);
            return IrBuilder.GetNewAluInst(op,zero,ret);
        }else if(op.equals("!")){
            ret = IrType.convertValueToType(ret, IrType.INT32);
            CmpInstr cmpInstr = IrBuilder.GetNewCmpInstr("==",new IrConstInt(0),ret);
            return IrBuilder.GetNewZextInstr(cmpInstr, IrType.INT32);
        }else{
            throw new RuntimeException("Unknown op: "+op);
        }
    }
}
