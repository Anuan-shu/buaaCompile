package frontend.Parser.Exp;

import frontend.Error;
import frontend.Parser.FuncDef.FuncRParams;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolType;

import java.util.ArrayList;

public class UnaryExp extends Node {
    public UnaryExp(GrammarType type, int index, ArrayList<Token> tokens) {
        super(type, index, tokens);
    }

    public void parser() {
        //UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
        if (this.peekToken(0).getLexeme().equals("+") || this.peekToken(0).getLexeme().equals("-") || this.peekToken(0).getLexeme().equals("!")) {
            //UnaryOp
            UnaryOp unaryOp = new UnaryOp(GrammarType.UnaryOp, this.getIndex(), this.getTokens());
            this.addChild(unaryOp);
            unaryOp.parser();

            //UnaryExp
            UnaryExp unaryExp = new UnaryExp(GrammarType.UnaryExp, this.getIndex(), this.getTokens());
            this.addChild(unaryExp);
            unaryExp.parser();
        } else if (this.peekToken(1).getLexeme().equals("(") && this.peekToken(0).getType().equals(Token.TokenType.IDENFR)) {
            //Ident
            ConstToken ident = new ConstToken(GrammarType.Ident, this.getIndex(), this.getTokens());
            this.addChild(ident);
            ident.parser();

            //(

            ConstToken leftParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
            this.addChild(leftParen);
            leftParen.parser();

            //FuncRParams
            if (!this.peekToken(0).getLexeme().equals(")")) {
                FuncRParams funcRParams = new FuncRParams(GrammarType.FuncRParams, this.getIndex(), this.getTokens());
                this.addChild(funcRParams);
                funcRParams.parser();
            }

            //)
            if (!this.peekToken(0).getLexeme().equals(")")) {
                Error error = new Error(Error.ErrorType.j, this.peekToken(-1).getLine(), "j");
                this.printToError(error);
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightParen);
            } else {
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(), this.getTokens());
                this.addChild(rightParen);
                rightParen.parser();
            }
        } else {
            //PrimaryExp
            PrimaryExp primaryExp = new PrimaryExp(GrammarType.PrimaryExp, this.getIndex(), this.getTokens());
            this.addChild(primaryExp);
            primaryExp.parser();
        }

        this.printTypeToFile();// UnaryExp
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }

    public UnaryExp GetChildAsUnaryExpByIndex(int i) {
        return (UnaryExp) this.getChildren().get(i);
    }

    public String GetChildAsFuncName() {
        return this.getChildren().get(0).getToken().getLexeme();
    }

    public int GetFuncNameLine() {
        return this.getChildren().get(0).getToken().getLine();
    }

    public FuncRParams GetChildAsFuncRParams() {
        return (FuncRParams) this.getChildren().get(2);
    }

    public PrimaryExp GetFirstChildAsPrimaryExp() {
        return (PrimaryExp) this.getChildren().get(0);
    }

    public SymbolType getExpType() {
        if (this.getChildren().size() == 1) {
            //PrimaryExp
            return this.GetFirstChildAsPrimaryExp().getExpType();
        } else if (this.getChildren().size() == 3 || this.getChildren().size() == 4) {
            //Ident '(' [FuncRParams] ')'
            String funcName = this.GetChildAsFuncName();
            Symbol funcSymbol = GlobalSymbolTable.searchSymbolByIdent(funcName, this.GetFuncNameLine());
            if (funcSymbol == null) {
                //函数未定义
                Error error = new Error(Error.ErrorType.c, this.GetFuncNameLine(), "c");
                error.printToError(error);
                return SymbolType.NOT_EXIST;
            }
            //返回函数的返回值类型
            if (funcSymbol.GetSymbolType() == SymbolType.VOID_FUNC) {
                return SymbolType.NOT_EXIST;
            } else {
                return SymbolType.CONST_INT;
            }

        } else {
            // UnaryOp UnaryExp
            return this.GetChildAsUnaryExpByIndex(1).getExpType();
        }
    }

    public String GetOp() {
        return this.getChildren().get(0).getToken().getLexeme();
    }

    public int Evaluate() {
        if (this.getChildren().size() == 1) {
            //PrimaryExp
            PrimaryExp primaryExp = this.GetFirstChildAsPrimaryExp();
            return primaryExp.Evaluate();
        } else if (this.getChildren().size() == 3 || this.getChildren().size() == 4) {
            //Ident '(' [FuncRParams] ')'
            //函数调用，暂不支持函数返回值计算，返回0
            return 0;
        } else {
            // UnaryOp UnaryExp
            String op = this.GetOp();
            UnaryExp unaryExp = this.GetChildAsUnaryExpByIndex(1);
            int val = unaryExp.Evaluate();
            if (op.equals("+")) {
                return val;
            } else if (op.equals("-")) {
                return -val;
            } else {
                // '!'
                return val == 0 ? 1 : 0;
            }
        }
    }
}
