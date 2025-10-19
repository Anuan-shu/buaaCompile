package frontend.Parser.FuncDef;

import frontend.Parser.Exp.Exp;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class FuncRParams extends Node {
    public FuncRParams(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        Exp exp = new Exp(GrammarType.Exp,this.getIndex(),this.getTokens());
        this.addChild(exp);
        exp.parser();

        while (this.peekToken(0).getLexeme().equals(",")){
            //,
            ConstToken comma = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(comma);
            comma.parser();

            Exp exp1 = new Exp(GrammarType.Exp,this.getIndex(),this.getTokens());
            this.addChild(exp1);
            exp1.parser();
        }
        this.printTypeToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
