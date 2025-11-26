package midend.Visit.Exp;

import frontend.Parser.Exp.MulExp;
import frontend.Parser.Exp.UnaryExp;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import midend.LLVM.Instruction.AluInst;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class VisitorMulExp {
    public static void VisitMulExp(MulExp mulExp) {
        // MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
        if(mulExp == null) {
            return;
        }
        for(Node child: mulExp.getChildren()) {
            if(child.getType().equals(GrammarType.UnaryExp)){
                VisitorUnaryExp.VisitUnaryExp((UnaryExp) child);
            }
        }
    }

    public static IrValue LLVMVisitMulExp(MulExp child) {
        // MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
        if (child == null) {
            return null;
        }
        ArrayList<Node> children = child.getChildren();
        UnaryExp unaryExp = (UnaryExp) children.get(0);
        IrValue left = VisitorUnaryExp.LLVMVisitUnaryExp(unaryExp);
        IrValue right = null;

        for(int i = 1; i < children.size(); i++) {
            String op = children.get(i++).getToken().getLexeme();
            UnaryExp unaryExp2 = (UnaryExp) children.get(i);
            right = VisitorUnaryExp.LLVMVisitUnaryExp(unaryExp2);

            left= IrType.convertValueToType(left,IrType.INT32);
            right = IrType.convertValueToType(right,IrType.INT32);

            left = IrBuilder.GetNewAluInst(op,left,right);
        }
        return left;
    }
}
