package frontend.Parser.MainFuncDef;

import frontend.Error;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class MainFuncDef extends Node {
    public MainFuncDef(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block
        ConstToken intToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
        this.addChild(intToken);
        intToken.parser();

        ConstToken mainToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
        this.addChild(mainToken);
        mainToken.parser();

        ConstToken lParentToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
        this.addChild(lParentToken);lParentToken.parser();

        if(!this.peekToken(0).getLexeme().equals(")")){
            frontend.Error error = new frontend.Error(Error.ErrorType.j, this.peekToken(-1).getLine(),"j");
            this.printToError(error);
            ConstToken rParentToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(rParentToken);
        }else {
            ConstToken rParentToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(rParentToken);
            rParentToken.parser();

        }
        // Block
        Block block = new Block(GrammarType.Block,this.getIndex(),this.getTokens());
        this.addChild(block);
        block.parser();


        this.printTypeToFile();//MainFuncDef
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public Block GetBlock() {
        return (Block)this.getChildren().get(4);
    }
}
