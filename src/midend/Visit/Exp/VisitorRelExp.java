package midend.Visit.Exp;

import frontend.Parser.Exp.AddExp;
import frontend.Parser.Exp.RelExp;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class VisitorRelExp {
    public static IrValue LLVMVisitRelExp(RelExp relExp) {
        ArrayList<AddExp> addExps = relExp.GetAddExps();
        ArrayList<String> relOps = relExp.GetRelOps();

        // 处理第一个AddExp
        IrValue leftValue = VisitorAddExp.LLVMVisitAddExp(addExps.get(0));
        IrValue rightValue = null;
        // 处理后续的AddExp和RelOp
        for (int i = 1; i < addExps.size(); i++) {
            rightValue = VisitorAddExp.LLVMVisitAddExp(addExps.get(i));

            leftValue = IrType.convertValueToType(leftValue, IrType.INT32);
            rightValue = IrType.convertValueToType(rightValue, IrType.INT32);

            leftValue = IrBuilder.GetNewCmpInstr(relOps.get(i - 1), leftValue, rightValue);
        }
        return leftValue;
    }
}
