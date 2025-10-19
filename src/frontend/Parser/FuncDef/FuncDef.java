package frontend.Parser.FuncDef;

import frontend.Error;
import frontend.Parser.MainFuncDef.Block;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class FuncDef extends Node {
    public FuncDef(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type, index,tokens);
    }

    public void parser() {
        //函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
        // FuncType
        FuncType funcType = new FuncType(GrammarType.FuncType, this.getIndex(), this.getTokens());
        this.addChild(funcType);
        funcType.parser();

        // Ident
        ConstToken ident = new ConstToken(GrammarType.Ident, this.getIndex(), this.getTokens());
        this.addChild(ident);
        ident.parser();
        // '('
        ConstToken leftParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
        this.addChild(leftParen);
        leftParen.parser();
        // [FuncFParams]
        if (this.peekToken(0).getLexeme().equals("int")) {
            FuncFParams funcFParams = new FuncFParams(GrammarType.FuncFParams, this.getIndex(), this.getTokens());
            this.addChild(funcFParams);
            funcFParams.parser();
        }
        // ')'
        if(!this.peekToken(0).getLexeme().equals(")")){
            Error error = new Error(Error.ErrorType.j, this.peekToken(-1).getLine(),"j");
            this.printToError(error);
        }else {
            ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(rightParen);
            rightParen.parser();
        }
        // Block
        Block block = new Block(GrammarType.Block, this.getIndex(), this.getTokens());
        this.addChild(block);
        block.parser();

        this.printTypeToFile();// FuncDef
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
