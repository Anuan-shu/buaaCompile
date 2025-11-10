package frontend.Parser.Decl;

import frontend.Error;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class VarDecl extends Node {
    public VarDecl(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type, index,  tokens);
    }

    public void parser() {
        // 变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';'
        // ['static']
        if (this.peekToken(0).getLexeme().equals("static")) {
            ConstToken staticToken = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
            this.addChild(staticToken);
            staticToken.parser();
        }
        // BType
        ConstToken bType = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
        this.addChild(bType);
        bType.parser();

        // VarDef
        VarDef varDef = new VarDef(GrammarType.VarDef, this.getIndex(), this.getTokens());
        this.addChild(varDef);
        varDef.parser();
        // { ',' VarDef }
        while (this.peekToken(0).getLexeme().equals(",")) {
            ConstToken commaToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(commaToken);
            commaToken.parser();

            VarDef additionalVarDef = new VarDef(GrammarType.VarDef, this.getIndex(), this.getTokens());
            this.addChild(additionalVarDef);
            additionalVarDef.parser();
        }

        // ';'
        if(!this.peekToken(0).getLexeme().equals(";")) {
            Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
            this.printToError(error);
            ConstToken semicolonToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(semicolonToken);
        }else {
            ConstToken semicolonToken = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(semicolonToken);
            semicolonToken.parser();
        }
        this.printTypeToFile();// VarDecl
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());

    }

    public ArrayList<VarDef> GetVarDefs() {
        ArrayList<VarDef> varDefs = new ArrayList<>();
        for (Node child : this.getChildren()) {
            if (child.getType() == GrammarType.VarDef) {
                varDefs.add((VarDef) child);
            }
        }
        return varDefs;
    }
    public boolean isStatic(){
        return this.getChildren().get(0).getType() == GrammarType.Token &&
                this.getChildren().get(0).getToken().getLexeme().equals("static");
    }
}
