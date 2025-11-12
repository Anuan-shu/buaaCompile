package frontend.Parser.FuncDef;

import frontend.Error;
import frontend.Parser.Token.BType;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import midend.Symbol.SymbolType;
import frontend.Token;

import java.util.ArrayList;

public class FuncFParam extends Node {
    public FuncFParam(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //函数形参 FuncFParam → BType Ident ['[' ']']
        // BType
        BType bType = new BType(GrammarType.BType,this.getIndex(), this.getTokens());
        this.addChild(bType);
        bType.parser();
        // Ident
        ConstToken ident = new ConstToken(GrammarType.Ident,this.getIndex(), this.getTokens());
        this.addChild(ident);
        ident.parser();
        // ['[' ']']
        if (this.peekToken(0).getLexeme().equals("[")){
            // [
            ConstToken leftBracket = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            this.addChild(leftBracket);
            leftBracket.parser();
            // ]
            if(!this.peekToken(0).getLexeme().equals("]")) {
                //错误处理，缺少右括号
                frontend.Error error = new frontend.Error(Error.ErrorType.k,this.peekToken(-1).getLine(), "k");
                this.printToError(error);
                ConstToken rightBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightBracket);
            }else {
                ConstToken rightBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightBracket);
                rightBracket.parser();
            }
        }
        this.printTypeToFile();//FuncFParam
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public String GetIdentName() {
        return this.getChildren().get(1).getToken().getLexeme();
    }

    public SymbolType GetSymbolType() {
        if(this.getChildren().size() != 2) {
            return SymbolType.INT_ARRAY;
        } else {
            return SymbolType.INT;
        }
    }

    public int GetLineNumber() {
        return this.getChildren().get(1).getToken().getLine();
    }
}
