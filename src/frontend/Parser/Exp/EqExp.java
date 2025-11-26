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

    public ArrayList<RelExp> GetRelExps() {
        ArrayList<RelExp> relExps = new ArrayList<>();
        for (Node child : this.getChildren()) {
            if (child.getType() == GrammarType.RelExp) {
                relExps.add((RelExp) child);
            }
        }
        return relExps;
    }

    public ArrayList<String> GetEqOps() {
        ArrayList<String> eqOps = new ArrayList<>();
        for (Node child : this.getChildren()) {
            if (child.getType() == GrammarType.Token) {
                String lexeme = ((ConstToken) child).getToken().getLexeme();
                if (lexeme.equals("==") || lexeme.equals("!=")) {
                    eqOps.add(lexeme);
                }else {
                    System.err.println("Error: Unexpected token in EqExp: " + lexeme);
                }
            }
        }
        return eqOps;
    }
}
