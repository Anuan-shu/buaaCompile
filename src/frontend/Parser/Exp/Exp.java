package frontend.Parser.Exp;

import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
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
}
