package midend.Visit.Exp;

import frontend.Parser.Exp.EqExp;
import frontend.Parser.Exp.RelExp;
import midend.LLVM.Const.IrConstInt;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class VisitorEqExp {
    public static IrValue LLVMVisitEqExp(EqExp eqExp) {
        ArrayList<RelExp> relExps = eqExp.GetRelExps();
        ArrayList<String> eqOps = eqExp.GetEqOps();

        // 处理第一个RelExp
        IrValue leftValue = VisitorRelExp.LLVMVisitRelExp(relExps.get(0));
        IrValue rightValue = null;

        for (int i = 1; i < relExps.size(); i++) {
            rightValue = VisitorRelExp.LLVMVisitRelExp(relExps.get(i));

            leftValue = IrType.convertValueToType(leftValue, IrType.INT32);
            rightValue = IrType.convertValueToType(rightValue, IrType.INT32);

            leftValue = IrBuilder.GetNewCmpInstr(eqOps.get(i - 1), leftValue, rightValue);// leftValue保存当前的比较结果
        }

        leftValue = IrType.convertValueToType(leftValue, IrType.INT32);

        return IrBuilder.GetNewCmpInstr("!=", leftValue, new IrConstInt(0));
    }
}
