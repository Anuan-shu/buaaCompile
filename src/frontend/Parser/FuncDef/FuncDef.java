package frontend.Parser.FuncDef;

import frontend.Error;
import frontend.Parser.MainFuncDef.Block;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Symbol.SymbolType;
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
            ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(rightParen);
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

    public boolean getFuncTypeIsVoid() {
        FuncType funcType = (FuncType) this.getChildren().get(0);
        return funcType.isFuncTypeIsVoid();
    }

    public String GetIdent() {
        return this.getChildren().get(1).getToken().getLexeme();
    }

    public SymbolType GetSymbolType() {
        if(this.getFuncTypeIsVoid()){
            return SymbolType.VOID_FUNC;
        }else {
            return SymbolType.INT_FUNC;
        }
    }

    public boolean HasFuncFParams() {
        return this.getChildren().get(3).getType() == GrammarType.FuncFParams;
    }

    public FuncFParams GetFuncFParams() {
        return (FuncFParams) this.getChildren().get(3);
    }

    public Block GetFuncBlock() {
        for(Node child:this.getChildren()){
            if(child.getType()== GrammarType.Block){
                return (Block) child;
            }
        }
        return null;
    }

    public int GetLineNumber() {
        return this.getChildren().get(1).getToken().getLine();
    }
}
