package frontend.Parser.Stmt;

import frontend.Parser.Exp.ConstExp;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class ConstInitVal extends Node {
    public ConstInitVal(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
        if(this.peekToken(0).getLexeme().equals("{")) {
            //'{'
            ConstToken leftBrace = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            leftBrace.setTokens(this.getTokens());
            this.addChild(leftBrace);leftBrace.parser();

            //可选的ConstExp
            if(!this.peekToken(0).getLexeme().equals("}")) {
                //ConstExp
                ConstExp constExp = new ConstExp(GrammarType.ConstExp,this.getIndex(),this.getTokens());
                constExp.setTokens(this.getTokens());
                this.addChild(constExp); constExp.parser();

                //重复的{',' ConstExp}
                while (this.peekToken(0).getLexeme().equals(",")) {
                    //','
                    ConstToken commaToken = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
                    commaToken.setTokens(this.getTokens());
                    this.addChild(commaToken);commaToken.parser();


                    //ConstExp
                    ConstExp nextConstExp = new ConstExp(GrammarType.ConstExp,this.getIndex(), this.getTokens());
                    nextConstExp.setTokens(this.getTokens());
                    this.addChild(nextConstExp);nextConstExp.parser();

                }
            }
            //'}'
            ConstToken rightBrace = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            rightBrace.setTokens(this.getTokens());
            this.addChild(rightBrace);rightBrace.parser();

        }else {
            //ConstExp
            ConstExp constExp = new ConstExp(GrammarType.ConstExp,this.getIndex(),this.getTokens());
            constExp.setTokens(this.getTokens());
            this.addChild(constExp);
            constExp.parser();

        }
        this.printTypeToFile();// ConstInitVal
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
