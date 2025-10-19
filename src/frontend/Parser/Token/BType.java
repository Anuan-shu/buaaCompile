package frontend.Parser.Token;

import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class BType extends Node {
    public BType(GrammarType grammarType,int index, ArrayList<Token> tokens) {
        super(grammarType,index,tokens);
    }

    public void parser() {
        this.setToken(this.peekToken(0));
        this.printToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex()+1);
    }
}
