package frontend.Parser.Exp;

import frontend.Error;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class LAndExp extends Node {
    public LAndExp(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
        // EqExp
        EqExp eqExp = new EqExp(GrammarType.EqExp,this.getIndex(),this.getTokens());
        this.addChild(eqExp);
        eqExp.parser();
        if(this.peekToken(0).getLexeme().equals("&&")){
            this.printTypeToFile();// LAndExp
        }else if(this.peekToken(0).getLexeme().equals("&")){
            Error error = new Error(Error.ErrorType.a,this.peekToken(-1).getLine(),"a");
            this.printToError(error);
        }
        while (this.peekToken(0).getLexeme().equals("&&")||
        this.peekToken(0).getLexeme().equals("&")) {
            // '&&'
            ConstToken andToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(andToken);
            andToken.parser();
            // EqExp
            EqExp eqExp2 = new EqExp(GrammarType.EqExp,this.getIndex(),this.getTokens());
            this.addChild(eqExp2);
            eqExp2.parser();
            if(this.peekToken(0).getLexeme().equals("&&")){
                this.printTypeToFile();// LAndExp
            }else if(this.peekToken(0).getLexeme().equals("&")){
                Error error = new Error(Error.ErrorType.a,this.peekToken(-1).getLine(),"a");
                this.printToError(error);
            }
        }

        this.printTypeToFile();// LAndExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public ArrayList<EqExp> GetEqExps() {
        ArrayList<EqExp> eqExps = new ArrayList<>();
        for (Node child : this.getChildren()) {
            if (child.getType() == GrammarType.EqExp) {
                eqExps.add((EqExp) child);
            }
        }
        return eqExps;
    }
}
