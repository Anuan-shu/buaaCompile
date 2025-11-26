package frontend.Parser.Exp;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import midend.Symbol.SymbolType;
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

    public UnaryExp GetFirstChildAsUnaryExp() {
        return (UnaryExp) this.getChildren().get(0);
    }

    public UnaryExp GetChildAsUnaryExpByIndex(int i) {
        return (UnaryExp) this.getChildren().get(i);
    }

    public boolean getExpType() {
        for(int i =0;i<this.getChildren().size();i+=2){
            UnaryExp unaryExp = this.GetChildAsUnaryExpByIndex(i);
            if(unaryExp.getExpType().equals(SymbolType.CONST_INT_ARRAY)||
            unaryExp.getExpType().equals(SymbolType.STATIC_INT_ARRAY)||
            unaryExp.getExpType().equals(SymbolType.INT_ARRAY)){
                return true;
            }
        }
        return false;
    }

    public int Evaluate() {
        int result = this.GetFirstChildAsUnaryExp().Evaluate();
        for (int i = 1; i < this.getChildren().size(); i += 2) {
            ConstToken op = (ConstToken) this.getChildren().get(i);
            UnaryExp unaryExp = this.GetChildAsUnaryExpByIndex(i + 1);
            if (op.getToken().getLexeme().equals("*")) {
                result *= unaryExp.Evaluate();
            } else if (op.getToken().getLexeme().equals("/")) {
                result /= unaryExp.Evaluate();
            } else if (op.getToken().getLexeme().equals("%")) {
                result %= unaryExp.Evaluate();
            }
        }
        return result;
    }
}
