package frontend.Parser.Decl;

import frontend.Error;
import frontend.Parser.Exp.ConstExp;
import frontend.Parser.Stmt.ConstInitVal;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Symbol.SymbolType;
import frontend.Token;

import java.util.ArrayList;

public class ConstDef extends Node {
    public ConstDef(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index, tokens);
    }

    public void parser() {
        ConstToken ident = new ConstToken(GrammarType.Ident, this.getIndex(), this.getTokens());
        ident.setTokens(this.getTokens());
        this.addChild(ident);
        ident.parser();

        //是否为数组
        if(this.peekToken(0).getLexeme().equals("[")) {
            //[
            ConstToken leftBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            leftBracket.setTokens(this.getTokens());
            this.addChild(leftBracket);
            leftBracket.parser();


            //常量表达式 ConstExp
            ConstExp constExp = new ConstExp(GrammarType.ConstExp, this.getIndex(), this.getTokens());
            constExp.setTokens(this.getTokens());
            this.addChild(constExp);
            constExp.parser();

            //]
            if(!this.peekToken(0).getLexeme().equals("]")) {
                Error error = new Error(Error.ErrorType.k, this.peekToken(-1).getLine(), "k");
                this.printToError(error);
                ConstToken rightBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightBracket);
            }else {
                ConstToken rightBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                rightBracket.setTokens(this.getTokens());
                this.addChild(rightBracket);
                rightBracket.parser();
            }
        }
        //=
        ConstToken equalToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
        equalToken.setTokens(this.getTokens());
        this.addChild(equalToken);
        equalToken.parser();

        //常量初始化值 ConstInitVal
        ConstInitVal constInitVal = new ConstInitVal(GrammarType.ConstInitVal, this.getIndex(), this.getTokens());
        constInitVal.setTokens(this.getTokens());
        this.addChild(constInitVal); constInitVal.parser();


        this.printTypeToFile();//ConstDef
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
    public String GetIdent() {
        return this.getChildren().get(0).getToken().getLexeme();
    }

    public SymbolType GetSymbolType() {
        if(this.getChildren().get(1).getToken().getLexeme().equals("=")) {
            return SymbolType.CONST_INT;
        }else {
            return SymbolType.CONST_INT_ARRAY;
        }
    }

    public int GetLineNumber() {
        return this.getChildren().get(0).getToken().getLine();
    }
}
