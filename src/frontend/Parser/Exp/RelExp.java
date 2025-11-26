package frontend.Parser.Exp;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class RelExp extends Node {
    public RelExp(GrammarType type,int inde, ArrayList<Token> tokens) {
        super(type,inde,tokens);
    }

    public void parser() {
        //关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
        // AddExp
        AddExp addExp = new AddExp(GrammarType.AddExp,this.getIndex(), this.getTokens());
        this.addChild(addExp);
        addExp.parser();
        if(this.peekToken(0).getLexeme().equals("<") || this.peekToken(0).getLexeme().equals(">")
                || this.peekToken(0).getLexeme().equals("<=") || this.peekToken(0).getLexeme().equals(">=")){
            this.printTypeToFile();// RelExp
        }
        while (this.peekToken(0).getLexeme().equals("<") || this.peekToken(0).getLexeme().equals(">")
                || this.peekToken(0).getLexeme().equals("<=") || this.peekToken(0).getLexeme().equals(">=")) {
            // '<' | '>' | '<=' | '>='
            ConstToken operatorToken = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            this.addChild(operatorToken);
            operatorToken.parser();
            // AddExp
            AddExp addExp2 = new AddExp(GrammarType.AddExp,this.getIndex(), this.getTokens());
            this.addChild(addExp2);
            addExp2.parser();
            if(this.peekToken(0).getLexeme().equals("<") || this.peekToken(0).getLexeme().equals(">")
                    || this.peekToken(0).getLexeme().equals("<=") || this.peekToken(0).getLexeme().equals(">=")){
                this.printTypeToFile();// RelExp
            }
        }

        this.printTypeToFile();// RelExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public ArrayList<AddExp> GetAddExps() {
        ArrayList<AddExp> addExps = new ArrayList<>();
        for (Node child : this.getChildren()) {
            if (child.getType() == GrammarType.AddExp) {
                addExps.add((AddExp) child);
            }
        }
        return addExps;
    }

    public ArrayList<String> GetRelOps() {
        ArrayList<String> relOps = new ArrayList<>();
        for (Node child : this.getChildren()) {
            if (child.getType() == GrammarType.Token) {
                relOps.add(((ConstToken) child).getToken().getLexeme());
            }
        }
        return relOps;
    }
}
