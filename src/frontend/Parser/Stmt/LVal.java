package frontend.Parser.Stmt;

import frontend.Error;
import frontend.Parser.Exp.Exp;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class LVal extends Node {
    public LVal(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index,tokens);
    }

    public void parser() {
        //左值表达式 LVal → Ident ['[' Exp ']']
        //Ident
        ConstToken ident = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
        this.addChild(ident);
        ident.parser();
        //['[' Exp ']']
        if(this.peekToken(0).getLexeme().equals("[")) {
            //[
            ConstToken leftBracket = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(leftBracket);
            leftBracket.parser();

            //Exp
            Exp exp = new Exp(GrammarType.Exp,this.getIndex(), this.getTokens());
            this.addChild(exp);
            exp.parser();

            //]
            if(!this.peekToken(0).getLexeme().equals("]")) {
                //错误处理，缺少右括号
                frontend.Error error = new frontend.Error(Error.ErrorType.k,this.peekToken(-1).getLine(), "k");
                this.printToError(error);
            }else {
                ConstToken rightBracket = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(rightBracket);
                rightBracket.parser();
            }
        }
        this.printTypeToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
