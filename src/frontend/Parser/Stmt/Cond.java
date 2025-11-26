package frontend.Parser.Stmt;

import frontend.Parser.Exp.LOrExp;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class Cond extends Node {
    public Cond(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index, tokens);
    }

    public void parser() {
        //条件表达式 Cond → LOrExp
        LOrExp lOrExp = new LOrExp(GrammarType.LOrExp,this.getIndex(),this.getTokens());
        this.addChild(lOrExp);
        lOrExp.parser();


        this.printTypeToFile();// Cond
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public LOrExp GetLOrExp() {
        return (LOrExp)this.getChildren().get(0);
    }
}
