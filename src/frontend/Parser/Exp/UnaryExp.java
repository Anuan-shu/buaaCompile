package frontend.Parser.Exp;

import frontend.Error;
import frontend.Parser.FuncDef.FuncRParams;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class UnaryExp extends Node {
    public UnaryExp (GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
        if(this.peekToken(0).getLexeme().equals("+")||
                this.peekToken(0).getLexeme().equals("-")||
                this.peekToken(0).getLexeme().equals("!")) {
            //UnaryOp
            UnaryOp unaryOp = new UnaryOp(GrammarType.UnaryOp,this.getIndex(), this.getTokens());
            this.addChild(unaryOp);
            unaryOp.parser();

            //UnaryExp
            UnaryExp unaryExp = new UnaryExp(GrammarType.UnaryExp,this.getIndex(), this.getTokens());
            this.addChild(unaryExp);
            unaryExp.parser();
        } else if(this.peekToken(1).getLexeme().equals("(")&&this.peekToken(0).getType().equals(Token.TokenType.IDENFR)) {
            //Ident
            ConstToken ident = new ConstToken(GrammarType.Ident,this.getIndex(), this.getTokens());
            this.addChild(ident);
            ident.parser();

            //(

            ConstToken leftParen = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            this.addChild(leftParen);
            leftParen.parser();

            //FuncRParams
            if(!this.peekToken(0).getLexeme().equals(")")) {
                FuncRParams funcRParams = new FuncRParams(GrammarType.FuncRParams,this.getIndex(), this.getTokens());
                this.addChild(funcRParams);
                funcRParams.parser();
            }

            //)
            if(!this.peekToken(0).getLexeme().equals(")")){
                frontend.Error error = new frontend.Error(Error.ErrorType.j, this.peekToken(-1).getLine(),"j");
                this.printToError(error);
            }else {
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightParen);
                rightParen.parser();
            }
        } else {
            //PrimaryExp
            PrimaryExp primaryExp = new PrimaryExp(GrammarType.PrimaryExp,this.getIndex(), this.getTokens());
            this.addChild(primaryExp);
            primaryExp.parser();
        }

        this.printTypeToFile();// UnaryExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
