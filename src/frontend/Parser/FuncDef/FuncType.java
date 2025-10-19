package frontend.Parser.FuncDef;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class FuncType extends Node {
    public FuncType(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index,tokens);
    }

    public void parser() {
        //函数类型 FuncType → 'void' | 'int'
        if(this.peekToken(0).getLexeme().equals("void") ||
           this.peekToken(0).getLexeme().equals("int")) {

            ConstToken token = new ConstToken(GrammarType.FuncType, this.getIndex(), this.getTokens());
            this.addChild(token);
            token.parser();
        }
        this.printTypeToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
