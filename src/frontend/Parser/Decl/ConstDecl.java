package frontend.Parser.Decl;

import frontend.Error;
import frontend.Parser.Token.BType;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class ConstDecl extends Node {
    public ConstDecl(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        ConstToken constToken=new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
        this.addChild(constToken);//constToken
        constToken.parser();


        BType bType=new BType(GrammarType.BType,this.getIndex(), this.getTokens());
        this.addChild(bType);//bType
        bType.parser();


        ConstDef constDef=new ConstDef(GrammarType.ConstDef,this.getIndex(), this.getTokens());
        this.addChild(constDef);//constDef
        constDef.parser();

        // 重复的constDef
        while (this.peekToken(0).getLexeme().equals(",")) {
            // ,
            ConstToken commaToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            commaToken.setTokens(this.getTokens());
            this.addChild(commaToken);
            commaToken.parser();
            // ConstDef

            ConstDef nextConstDef = new ConstDef(GrammarType.ConstDef, this.getIndex(), this.getTokens());
            this.addChild(nextConstDef);
            nextConstDef.parser();

        }

        if(this.peekToken(0).getLexeme().equals(";")) {
            // ;
            ConstToken semicolonToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            semicolonToken.setTokens(this.getTokens());
            this.addChild(semicolonToken);
            semicolonToken.parser();


        }else {
            // 错误处理，缺少分号
            Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
            this.printToError(error);
        }
        this.printTypeToFile();//ConstDecl
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
