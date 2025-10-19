package frontend.Parser.Exp;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class LOrExp extends Node {
    public LOrExp(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
        // LAndExp
        LAndExp lAndExp = new LAndExp(GrammarType.LAndExp,this.getIndex(),this.getTokens());
        this.addChild(lAndExp);
        lAndExp.parser();
        if(this.peekToken(0).getLexeme().equals("||")){
            this.printTypeToFile();// LOrExp
        }
        while (this.peekToken(0).getLexeme().equals("||")) {
            // '||'
            ConstToken orToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(orToken);
            orToken.parser();
            // LAndExp
            LAndExp lAndExp2 = new LAndExp(GrammarType.LAndExp,this.getIndex(),this.getTokens());
            this.addChild(lAndExp2);
            lAndExp2.parser();
            if(this.peekToken(0).getLexeme().equals("||")){
                this.printTypeToFile();// LOrExp
            }
        }

        this.printTypeToFile();// LOrExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
