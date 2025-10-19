package frontend.Parser.Exp;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class EqExp extends Node {
    public EqExp(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
        // RelExp
        RelExp relExp = new RelExp(GrammarType.RelExp,this.getIndex(),this.getTokens());
        this.addChild(relExp);
        relExp.parser();
        if(this.peekToken(0).getLexeme().equals("==") || this.peekToken(0).getLexeme().equals("!=")){
            this.printTypeToFile();// EqExp
        }
        while (this.peekToken(0).getLexeme().equals("==") || this.peekToken(0).getLexeme().equals("!=")) {
            // '==' | '!='
            ConstToken operatorToken = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            this.addChild(operatorToken);
            operatorToken.parser();
            // RelExp
            RelExp relExp2 = new RelExp(GrammarType.RelExp,this.getIndex(), this.getTokens());
            this.addChild(relExp2);
            relExp2.parser();
            if(this.peekToken(0).getLexeme().equals("==") || this.peekToken(0).getLexeme().equals("!=")){
                this.printTypeToFile();// EqExp
            }
        }

        this.printTypeToFile();// EqExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
