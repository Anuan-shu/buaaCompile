package frontend.Parser.Exp;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import midend.Symbol.SymbolType;
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

    public MulExp GetFirstChildAsMulExp() {
        return (MulExp) this.getChildren().get(0);
    }

    public MulExp GetChildAsMulExpByIndex(int i) {
        return (MulExp) this.getChildren().get(i);
    }

    public SymbolType getExpType() {
        for(int i =0;i<this.getChildren().size();i+=2){
            MulExp mulExp = this.GetChildAsMulExpByIndex(i);
            if(mulExp.getExpType()){
                return SymbolType.ARRAY;
            }
        }
        return SymbolType.NOT_ARRAY;
    }

    public int Evaluate() {
        int result = this.GetFirstChildAsMulExp().Evaluate();
        for (int i = 1; i < this.getChildren().size(); i += 2) {
            ConstToken op = (ConstToken) this.getChildren().get(i);
            MulExp mulExp = this.GetChildAsMulExpByIndex(i + 1);
            if (op.getToken().getLexeme().equals("+")) {
                result += mulExp.Evaluate();
            } else if (op.getToken().getLexeme().equals("-")) {
                result -= mulExp.Evaluate();
            }
        }
        return result;
    }
}
