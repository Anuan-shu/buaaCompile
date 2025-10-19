package frontend.Parser.MainFuncDef;

import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class Block extends Node {
    public Block(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index, tokens);
    }

    public void parser() {
        //语句块 Block → '{' { BlockItem } '}'
        // '{'
        ConstToken leftBrace = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
        this.addChild(leftBrace);
        leftBrace.parser();
        // { BlockItem }
        while (!this.peekToken(0).getLexeme().equals("}")) {
            BlockItem blockItem = new BlockItem(GrammarType.BlockItem,this.getIndex(), this.getTokens());
            this.addChild(blockItem);
            blockItem.parser();
        }
        // '}'
        ConstToken rightBrace = new ConstToken(GrammarType.Token,this.getIndex(), this.getTokens());
        this.addChild(rightBrace);
        rightBrace.parser();
        this.printTypeToFile();// Block
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
