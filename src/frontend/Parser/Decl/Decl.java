package frontend.Parser.Decl;

import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class Decl extends Node {
    public Decl(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        if(this.peekToken(0).getLexeme().equals("const")) {
            //常量声明 ConstDecl
            ConstDecl constDecl = new ConstDecl(GrammarType.ConstDecl,this.getIndex(),this.getTokens());
            constDecl.setTokens(this.getTokens());
            this.addChild(constDecl);
            constDecl.parser();

        }else {
            //变量声明 VarDecl
            VarDecl varDecl = new VarDecl(GrammarType.VarDecl,this.getIndex(),this.getTokens());
            varDecl.setTokens(this.getTokens());
            this.addChild(varDecl);varDecl.parser();

        }
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public boolean isConstDecl() {
        return this.getChildren().get(0).getType() == GrammarType.ConstDecl;
    }

    public ConstDecl getConstDecl() {
        return (ConstDecl) this.getChildren().get(0);
    }

    public VarDecl getVarDecl() {
        return (VarDecl) this.getChildren().get(0);
    }
}
