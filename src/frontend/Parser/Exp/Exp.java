package frontend.Parser.Exp;

import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import midend.Symbol.SymbolType;
import frontend.Token;

import java.util.ArrayList;

public class Exp extends Node {
    public Exp(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index, tokens);
    }

    public void parser() {
        AddExp addExp = new AddExp(GrammarType.AddExp, this.getIndex(), this.getTokens());
        this.addChild(addExp);
        addExp.parser();

        this.printTypeToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public AddExp GetChildAsAddExp() {
        return (AddExp) this.getChildren().get(0);
    }

    public SymbolType getExpType() {
        return this.GetChildAsAddExp().getExpType();
    }

    public int Evaluate() {
        AddExp addExp = (AddExp)this.getChildren().get(0);
        return addExp.Evaluate();
    }
}
