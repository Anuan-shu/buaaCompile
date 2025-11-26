package midend.Visit.Stmt;

import frontend.Error;
import frontend.Parser.Exp.Exp;
import frontend.Parser.Stmt.Stmt;
import midend.LLVM.Const.IrConstString;
import midend.LLVM.Instruction.PrintIntInstr;
import midend.LLVM.Instruction.PrintStrInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrValue;
import midend.Visit.Exp.VisitorExp;

import java.util.ArrayList;

public class VisitorPrintf {
    public static void VisitPrint(Stmt stmt) {
        String stringConst = stmt.GetStringConst();
        int numString=0;
        //%d的数量
        for(int i=0;i<stringConst.length()-1;i++){
            if(stringConst.charAt(i)=='%'&&stringConst.charAt(i+1)=='d'){
                numString++;
            }
        }
        ArrayList<Exp> exps = stmt.GetExps();
        if(numString!=exps.size()){
            Error error = new Error(Error.ErrorType.l,stmt.GetPrintLine(),"l");
            error.printToError(error);
        }
        for(Exp exp:exps){
            VisitorExp.VisitExp(exp);
        }
    }

    public static void LLVMVisitPrint(Stmt stmt) {
        String stringConst = stmt.GetStringConst();
        //去除引号
        stringConst = stringConst.substring(1,stringConst.length()-1);
        ArrayList<Exp> exps = stmt.GetExps();
        int numString=0;
        ArrayList<IrValue> printValues = new ArrayList<>();
        for (Exp exp:exps){
            IrValue value = VisitorExp.LLVMVisitExp(exp);
            printValues.add(value);
        }

        StringBuilder formatBuilder = new StringBuilder();
        for(int i=0;i<stringConst.length();i++){
            char ch = stringConst.charAt(i);
            if(ch=='%'){
                if(!formatBuilder.isEmpty()){
                    IrConstString irConstString = IrBuilder.GetNewIrConstString(formatBuilder.toString());
                    PrintStrInstr printStrInstr = IrBuilder.GetNewPrintStrInstr(irConstString);
                    formatBuilder.setLength(0);
                }

                if(stringConst.charAt(i+1)=='d'){
                    IrValue value = printValues.get(numString);
                    numString++;
                    value = IrType.convertValueToType(value, IrType.INT32);
                    PrintIntInstr printIntInstr = IrBuilder.GetNewPrintIntInstr(value);
                    i++;
                }
            }else if(ch=='\\'){
                formatBuilder.append("\\n");
                i++;
            }else {
                formatBuilder.append(ch);
            }
        }
        if(!formatBuilder.isEmpty()){
            IrConstString irConstString = IrBuilder.GetNewIrConstString(formatBuilder.toString());
            PrintStrInstr printStrInstr = IrBuilder.GetNewPrintStrInstr(irConstString);
        }
    }
}
