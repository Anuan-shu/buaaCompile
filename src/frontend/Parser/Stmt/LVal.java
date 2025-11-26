package frontend.Parser.Stmt;

import frontend.Error;
import frontend.Parser.Exp.Exp;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolType;
import frontend.Token;

import java.util.ArrayList;

public class LVal extends Node {
    public LVal(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index, tokens);
    }

    public void parser() {
        //左值表达式 LVal → Ident ['[' Exp ']']
        //Ident
        ConstToken ident = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
        this.addChild(ident);
        ident.parser();
        //['[' Exp ']']
        if (this.peekToken(0).getLexeme().equals("[")) {
            //[
            ConstToken leftBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(leftBracket);
            leftBracket.parser();

            //Exp
            Exp exp = new Exp(GrammarType.Exp, this.getIndex(), this.getTokens());
            this.addChild(exp);
            exp.parser();

            //]
            if (!this.peekToken(0).getLexeme().equals("]")) {
                //错误处理，缺少右括号
                frontend.Error error = new frontend.Error(Error.ErrorType.k, this.peekToken(-1).getLine(), "k");
                this.printToError(error);
                ConstToken rightBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightBracket);
            } else {
                ConstToken rightBracket = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightBracket);
                rightBracket.parser();
            }
        }
        this.printTypeToFile();
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public String getIdent() {
        return this.getChildren().get(0).getToken().getLexeme();
    }

    public int GetLineNumber() {
        return this.getChildren().get(0).getToken().getLine();
    }

    public SymbolType getExpType() {
        if (this.getChildren().size() == 4) {
            return SymbolType.CONST_INT;
        }
        //查符号表
        String ident = this.getIdent();
        Symbol symbolExp = GlobalSymbolTable.searchSymbolByIdent(ident);
        if (symbolExp != null) {
            return symbolExp.GetSymbolType();
        } else {
            return SymbolType.NOT_EXIST;
        }
    }

    public boolean hasIndexExp() {
        return this.getChildren().size() == 4;
    }

    public Exp getIndexExp() {
        if (this.hasIndexExp()) {
            return (Exp) this.getChildren().get(2);
        }
        return null;
    }

    public int Evaluate() {
        if (this.hasIndexExp()) {
            //数组元素访问，返回对应下标的值
            Symbol symbolExp = GlobalSymbolTable.searchSymbolByIdent(this.getIdent());
            if (symbolExp != null) {
                int index = this.getIndexExp().Evaluate();
                if (index < 0 || index >= symbolExp.getInitValues().size()) {
                    //数组下标越界
                    return 0;
                }
                return symbolExp.getInitValues().get(index);
            }
        } else {
            //变量访问，返回变量的值
            Symbol symbolExp = GlobalSymbolTable.searchSymbolByIdent(this.getIdent());
            if (symbolExp != null) {
                if (symbolExp.getInitValues().isEmpty()) {
                    return 0;
                }
                return symbolExp.getInitValues().get(0);
            }
        }
        return 0;
    }
}
