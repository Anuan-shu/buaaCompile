package frontend.Visit.Exp;

import frontend.Parser.Exp.AddExp;
import frontend.Parser.Exp.MulExp;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;

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
}
