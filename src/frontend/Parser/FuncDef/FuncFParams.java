package frontend.Parser.FuncDef;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class FuncFParams extends Node {
    public FuncFParams(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
        FuncFParam funcFParam = new FuncFParam(GrammarType.FuncFParam,this.getIndex(), this.getTokens());
        this.addChild(funcFParam);
        funcFParam.parser();

        while (this.peekToken(0).getLexeme().equals(",")) {
            // ','
            ConstToken comma = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
            this.addChild(comma);
            comma.parser();

            funcFParam = new FuncFParam(GrammarType.FuncFParam,this.getIndex(), this.getTokens());
            this.addChild(funcFParam);
            funcFParam.parser();
        }
        this.printTypeToFile();//FuncFParams
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
