package frontend.Parser.MainFuncDef;

import frontend.Parser.Decl.Decl;
import frontend.Parser.Stmt.Stmt;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class BlockItem extends Node {
    public BlockItem(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
        //块项 BlockItem → Decl | Stmt
        if (isDeclStart(this.peekToken(0))) {
            //Decl
             Decl decl = new Decl(GrammarType.Decl,this.getIndex(), this.getTokens());
             this.addChild(decl);
             decl.parser();
        } else {
            //Stmt
             Stmt stmt = new Stmt(GrammarType.Stmt,this.getIndex(), this.getTokens());
             this.addChild(stmt);
             stmt.parser();
        }
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    private boolean isDeclStart(Token token) {
       if(token.getLexeme().equals("const") ||
                 token.getLexeme().equals("int") ||
               token.getLexeme().equals("static")) {
            return true;
        }else {
            return false;
       }
    }
}
