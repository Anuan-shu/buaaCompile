package frontend.Parser;

import frontend.Parser.Decl.Decl;
import frontend.Parser.FuncDef.FuncDef;
import frontend.Parser.MainFuncDef.MainFuncDef;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class ComUnit extends Node {

    public ComUnit(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type,index, tokens);
    }

    public ComUnit parser() {
        //编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
        int i = 0;
        while (true) {
            Token token = this.peekToken(i);
            if (isMainFuncDefStart(token)) {
                MainFuncDef mainFuncDef = new MainFuncDef(GrammarType.MainFuncDef,this.getIndex(),this.getTokens());
                this.addChild(mainFuncDef);
                mainFuncDef.parser();
                break;
            } else if (isFuncDefStart(token)) {
                FuncDef funcDef = new FuncDef(GrammarType.FuncDef,this.getIndex(),this.getTokens());
                this.addChild(funcDef);
                funcDef.parser();

            }else if (isDeclStart(token)) {
                Decl decl = new Decl(GrammarType.Decl,this.getIndex(),this.getTokens());
                this.addChild(decl);
                decl.parser();
            }else {
                throw new RuntimeException("无法识别的编译单元起始符: " + token.getLexeme());
            }
        }
        this.printTypeToFile();
        return this;
    }

    private boolean isMainFuncDefStart(Token token) {
        //MainFuncDef以"int main("开头
        return token.getLexeme().equals("int") &&
               this.peekToken(1).getLexeme().equals("main") &&
               this.peekToken(2).getLexeme().equals("(");
    }

    private boolean isFuncDefStart(Token token) {
        //FuncDef以"void"或"int"开头
        return (!this.peekToken(1).getLexeme().equals("main")) &&
               (token.getLexeme().equals("void") ||
                token.getLexeme().equals("int"))&&this.peekToken(2).getLexeme().equals("(");
    }

    private boolean isDeclStart(Token token) {
        //Decl以"int"或"const"或"static"开头
        return token.getLexeme().equals("int") ||
               token.getLexeme().equals("const") ||
               token.getLexeme().equals("static");
    }

    public ArrayList<Decl> GetDecls() {
        ArrayList<Decl> decls = new ArrayList<>();
        for(Node child:this.getChildren()){
            if(child instanceof Decl){
                decls.add((Decl)child);
            }
        }
        return decls;
    }

    public ArrayList<FuncDef> GetFuncDefs() {
        ArrayList<FuncDef> funcDefs = new ArrayList<>();
        for(Node child:this.getChildren()){
            if(child instanceof FuncDef){
                funcDefs.add((FuncDef)child);
            }
        }
        return funcDefs;
    }

    public MainFuncDef GetMainFuncDef() {
        MainFuncDef mainFuncDef = null;
        for(Node child:this.getChildren()){
            if(child instanceof MainFuncDef){
                mainFuncDef = (MainFuncDef)child;
                break;
            }
        }
        return mainFuncDef;
    }
}
