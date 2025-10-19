package frontend.Parser.Stmt;

import frontend.Parser.Exp.Exp;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class InitVal extends Node {
    public InitVal(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
        if(this.peekToken(0).getLexeme().equals("{")) {
            //'{'
            ConstToken leftBrace = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
            this.addChild(leftBrace);
            leftBrace.parser();

            // [ Exp { ',' Exp } ]
            if (!this.peekToken(0).getLexeme().equals("}")) {
                //Exp
                Exp exp = new Exp(GrammarType.Exp, this.getIndex(),this.getTokens());
                this.addChild(exp);
                exp.parser();

                //{ ',' Exp }
                while (this.peekToken(0).getLexeme().equals(",")) {
                    //','
                    ConstToken comma = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                    this.addChild(comma);
                    comma.parser();

                    //Exp
                    Exp exp1 = new Exp(GrammarType.Exp, this.getIndex(),this.getTokens());
                    this.addChild(exp1);
                    exp1.parser();
                }
            }

            //'}'
            ConstToken rightBrace = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
            this.addChild(rightBrace);
            rightBrace.parser();
        }else {
            //Exp
            Exp exp = new Exp(GrammarType.Exp, this.getIndex(),this.getTokens());
            this.addChild(exp);
            exp.parser();
        }
        this.printTypeToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
