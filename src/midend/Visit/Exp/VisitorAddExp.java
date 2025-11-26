package midend.Visit.Exp;

import frontend.Parser.Exp.AddExp;
import frontend.Parser.Exp.MulExp;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class VisitorAddExp {
    public static void VisitAddExp(AddExp addExp) {
        // AddExp → MulExp | AddExp ('+' | '−') MulExp
        if(addExp == null) {
            return;
        }
        for(Node child : addExp.getChildren()) {
            if(child.getType().equals(GrammarType.MulExp)){
                VisitorMulExp.VisitMulExp((MulExp) child);
            }
        }
    }

    public static IrValue LLVMVisitAddExp(AddExp addExp) {
        // AddExp → MulExp | AddExp ('+' | '−') MulExp
        if (addExp == null) {
            return null;
        }
        ArrayList<Node> children = addExp.getChildren();
        MulExp mulExp1 = (MulExp) children.get(0);
        IrValue leftValue = VisitorMulExp.LLVMVisitMulExp(mulExp1);
        IrValue rightValue = null;

        for(int i = 1; i < children.size(); i++) {
            String op = children.get(i++).getToken().getLexeme();
            MulExp mulExp2 = (MulExp) children.get(i);
            rightValue = VisitorMulExp.LLVMVisitMulExp(mulExp2);

            leftValue = IrType.convertValueToType(leftValue,IrType.INT32);
            rightValue = IrType.convertValueToType(rightValue,IrType.INT32);

            leftValue = IrBuilder.GetNewAluInst(op, leftValue, rightValue);
        }
        return leftValue;
    }
}
