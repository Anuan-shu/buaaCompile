package frontend.Parser.Exp;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class AddExp extends Node {
    public AddExp(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
        MulExp mulExp = new MulExp(GrammarType.MulExp,this.getIndex(),this.getTokens());
        this.addChild(mulExp);
        mulExp.parser();
        if(this.peekToken(0).getLexeme().equals("+")||
                this.peekToken(0).getLexeme().equals("-")){
            this.printTypeToFile();// AddExp
        }
        //+或-
        while (this.peekToken(0).getLexeme().equals("+")||
                this.peekToken(0).getLexeme().equals("-")) {
            ConstToken op = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(op);
            op.parser();

            MulExp mulExp1 = new MulExp(GrammarType.MulExp,this.getIndex(),this.getTokens());
            this.addChild(mulExp1);
            mulExp1.parser();
            if(this.peekToken(0).getLexeme().equals("+")||
                    this.peekToken(0).getLexeme().equals("-")){
                this.printTypeToFile();// AddExp
            }
        }
        this.printTypeToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
