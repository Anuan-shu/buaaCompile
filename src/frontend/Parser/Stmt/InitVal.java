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

    public boolean isExp() {
        return this.getChildren().get(0).getType().equals(GrammarType.Exp);
    }

    public Exp getExp() {
        return (Exp)this.getChildren().get(0);
    }

    public ArrayList<Exp> getExpList() {
        ArrayList<Exp> expList = new ArrayList<>();
        for(Node child : this.getChildren()) {
            if(child.getType().equals(GrammarType.Exp)) {
                expList.add((Exp)child);
            }
        }
        return expList;
    }

    public ArrayList<Integer> Evaluate() {
        ArrayList<Integer> initValues = new ArrayList<>();
        if(this.isExp()) {
            Exp exp = this.getExp();
            initValues.add(exp.Evaluate());
        }else {
            ArrayList<Exp> expList = this.getExpList();
            for(Exp exp : expList) {
                initValues.add(exp.Evaluate());
            }
        }
        return initValues;
    }
}
