package frontend.Parser.Stmt;

import frontend.Parser.Exp.Exp;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class ForStmt extends Node {
    public ForStmt(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //语句 ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
        // LVal
        LVal lVal = new LVal(GrammarType.LVal,this.getIndex(), this.getTokens());
        this.addChild(lVal);
        lVal.parser();
        // '='
        ConstToken assignToken = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
        this.addChild(assignToken);
        assignToken.parser();

        // Exp
        Exp exp = new Exp(GrammarType.Exp,this.getIndex(), this.getTokens());
        exp.setTokens(this.getTokens());
        this.addChild(exp);
        exp.parser();
        // { ',' LVal '=' Exp }
        while (this.peekToken(0).getLexeme().equals(",")) {
            // ','
            ConstToken commaToken = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            this.addChild(commaToken);
            commaToken.parser();
            // LVal
            LVal nextLVal = new LVal(GrammarType.LVal,this.getIndex(), this.getTokens());
            this.addChild(nextLVal);
            nextLVal.parser();
            // '='
            ConstToken nextAssignToken = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            this.addChild(nextAssignToken);
            nextAssignToken.parser();
            // Exp
            Exp nextExp = new Exp(GrammarType.Exp,this.getIndex(), this.getTokens());
            this.addChild(nextExp);
            nextExp.parser();
        }
        this.printTypeToFile();// ForStmt
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
