package frontend.Parser.Decl;

import frontend.Error;
import frontend.Parser.Exp.ConstExp;
import frontend.Parser.Stmt.InitVal;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class VarDef extends Node {
    public VarDef(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type, index, tokens);
    }

    public void parser() {
        //变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal
        //Ident
        ConstToken ident = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
        this.addChild(ident);
        ident.parser();
        //['[' ConstExp ']']
        if(this.peekToken(0).getLexeme().equals("[")) {
            //[
            ConstToken leftBracket = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(leftBracket);
            leftBracket.parser();

            //ConstExp
            ConstExp constExp = new ConstExp(GrammarType.ConstExp,this.getIndex(),this.getTokens());
            this.addChild(constExp);
            constExp.parser();

            //]
            if(!this.peekToken(0).getLexeme().equals("]")) {
                //错误处理，缺少右括号
                Error error = new Error(Error.ErrorType.k,this.peekToken(-1).getLine(), "k");
                this.printToError(error);
            }else{
            ConstToken rightBracket = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(rightBracket);
            rightBracket.parser();
            }
        }
        //| '=' InitVal
        if(this.peekToken(0).getLexeme().equals("=")) {
            //=
            ConstToken equalToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(equalToken);
            equalToken.parser();

            //InitVal
            InitVal initVal = new InitVal(GrammarType.InitVal,this.getIndex(),this.getTokens());
            this.addChild(initVal);
            initVal.parser();
        }
        this.printTypeToFile();// VarDef
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
