package midend.Visit.Exp;

import frontend.Parser.Exp.MulExp;
import frontend.Parser.Exp.UnaryExp;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;

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
}
