package frontend.Parser.Exp;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class MulExp extends Node {
    public MulExp(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index,tokens);
    }

    public void parser() {
        UnaryExp unaryExp = new UnaryExp(GrammarType.UnaryExp, this.getIndex(), this.getTokens());
        this.addChild(unaryExp);
        unaryExp.parser();
        if(this.peekToken(0).getLexeme().equals("*")||
                this.peekToken(0).getLexeme().equals("/")||
                this.peekToken(0).getLexeme().equals("%")){
            this.printTypeToFile();// MulExp
        }
        //*或/或%
        while (this.peekToken(0).getLexeme().equals("*")||
        this.peekToken(0).getLexeme().equals("/")||
                this.peekToken(0).getLexeme().equals("%")) {
            //op
            ConstToken op = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(op);
            op.parser();

            //UnaryExp
            UnaryExp unaryExp1 = new UnaryExp(GrammarType.UnaryExp, this.getIndex(), this.getTokens());
            this.addChild(unaryExp1);
            unaryExp1.parser();
            if(this.peekToken(0).getLexeme().equals("*")||
                    this.peekToken(0).getLexeme().equals("/")||
                    this.peekToken(0).getLexeme().equals("%")){
                this.printTypeToFile();// MulExp
            }
        }

        this.printTypeToFile();// MulExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
