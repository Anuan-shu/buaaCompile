package frontend.Parser.Exp;

import frontend.Error;
import frontend.Parser.Stmt.LVal;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;
import midend.Symbol.SymbolType;

import java.util.ArrayList;

public class PrimaryExp extends Node {
    public PrimaryExp(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index, tokens);
    }

    public void parser() {
        //PrimaryExp → '(' Exp ')' | LVal | Number
        if (this.peekToken(0).getLexeme().equals("(")) {
            //(
            ConstToken leftParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(leftParen);
            leftParen.parser();

            //Exp
            Exp exp = new Exp(GrammarType.Exp, this.getIndex(), this.getTokens());
            this.addChild(exp);
            exp.parser();

            //)
            if (!this.peekToken(0).getLexeme().equals(")")) {
                frontend.Error error = new frontend.Error(Error.ErrorType.j, this.peekToken(-1).getLine(), "j");
                this.printToError(error);
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightParen);
            } else {
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightParen);
                rightParen.parser();
            }
        } else if (this.peekToken(0).getType().equals(Token.TokenType.INTCON)) {
            //Number
            NumberConst numConst = new NumberConst(GrammarType.Number, this.getIndex(), this.getTokens());
            this.addChild(numConst);
            numConst.parser();
        } else {
            //LVal
            LVal lVal = new LVal(GrammarType.LVal, this.getIndex(), this.getTokens());
            this.addChild(lVal);
            lVal.parser();
        }
        this.printTypeToFile();// PrimaryExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public Exp GetChildAsExp() {
        return (Exp) this.getChildren().get(1);
    }

    public boolean IsChildLVal() {
        return this.getChildren().get(0).getType().equals(GrammarType.LVal);
    }

    public LVal GetChildAsLVal() {
        return (LVal) this.getChildren().get(0);
    }

    public SymbolType getExpType() {
        if (this.IsChildLVal()) {
            return this.GetChildAsLVal().getExpType();
        } else if (this.getChildren().get(0).getType().equals(GrammarType.Number)) {
            return SymbolType.CONST_INT;
        } else {
            return this.GetChildAsExp().getExpType();
        }
    }

    public int GetChildAsNumber() {
        return Integer.parseInt(this.getChildren().get(0).getToken().getLexeme());
    }

    public int Evaluate() {
        if (this.IsChildLVal()) {
            return this.GetChildAsLVal().Evaluate();
        } else if (this.getChildren().get(0).getType().equals(GrammarType.Number)) {
            return this.GetChildAsNumber();
        } else {
            return this.GetChildAsExp().Evaluate();
        }
    }
}
