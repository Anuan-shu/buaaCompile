package frontend.Parser.Stmt;

import frontend.Error;
import frontend.Parser.Exp.Exp;
import frontend.Parser.MainFuncDef.Block;
import frontend.Parser.Token.ConstToken;
import frontend.Parser.Tree.GrammarType;
import frontend.Parser.Tree.Node;
import frontend.Token;

import java.util.ArrayList;

public class Stmt extends Node {
    public Stmt(GrammarType type,int index, ArrayList<Token> tokens) {
        super(type,index,tokens);
    }

    public void parser() {
       /*
       语句 Stmt → LVal '=' Exp ';'| [Exp] ';'| Block| 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
        | 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt| 'break' ';'| 'continue' ';'| 'return' [Exp] ';'
        | 'printf''('StringConst {','Exp}')'';'
        */
        if(this.peekToken(0).getLexeme().equals("if")) {
            //if语句
            ConstToken ifToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(ifToken);
            ifToken.parser();
            // '('
            ConstToken leftParen = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(leftParen);
            leftParen.parser();
            // Cond
            Cond cond = new Cond(GrammarType.Cond,this.getIndex(),this.getTokens());
            this.addChild(cond);
            cond.parser();
            // ')'
            if(!this.peekToken(0).getLexeme().equals(")")){
                frontend.Error error = new frontend.Error(Error.ErrorType.j, this.peekToken(-1).getLine(),"j");
                this.printToError(error);
            }else {
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(rightParen);
                rightParen.parser();
            }
            // Stmt
            Stmt thenStmt = new Stmt(GrammarType.Stmt,this.getIndex(),this.getTokens());
            this.addChild(thenStmt);
            thenStmt.parser();
            // [ 'else' Stmt ]
            if(this.peekToken(0).getLexeme().equals("else")) {
                ConstToken elseToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
                this.addChild(elseToken);
                elseToken.parser();

                Stmt elseStmt = new Stmt(GrammarType.Stmt,this.getIndex(),this.getTokens());
                this.addChild(elseStmt);
                elseStmt.parser();
            }
        } else if(this.peekToken(0).getLexeme().equals("{")) {
            //块语句
            Block blockStmt = new Block(GrammarType.Block,this.getIndex(),this.getTokens());
            this.addChild(blockStmt);
            blockStmt.parser();
        }else if(this.peekToken(0).getLexeme().equals("for")) {
            //for语句
            ConstToken forToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(forToken);
            forToken.parser();
            // '('
            ConstToken leftParen = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(leftParen);
            leftParen.parser();
            // [ForStmt]
            if(!this.peekToken(0).getLexeme().equals(";")) {
                ForStmt forStmtInit = new ForStmt(GrammarType.ForStmt,this.getIndex(),this.getTokens());
                this.addChild(forStmtInit);
                forStmtInit.parser();
            }
            // ';'
            ConstToken firstSemicolon = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(firstSemicolon);
            firstSemicolon.parser();
            // [Cond]
            if(!this.peekToken(0).getLexeme().equals(";")) {
                Cond cond = new Cond(GrammarType.Cond,this.getIndex(),this.getTokens());
                this.addChild(cond);
                cond.parser();
            }
            // ';'
            ConstToken secondSemicolon = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(secondSemicolon);
            secondSemicolon.parser();
            // [ForStmt]
            if(!this.peekToken(0).getLexeme().equals(")")) {
                ForStmt forStmtUpdate = new ForStmt(GrammarType.ForStmt,this.getIndex(),this.getTokens());
                this.addChild(forStmtUpdate);
                forStmtUpdate.parser();
            }
            // ')'
            ConstToken rightParen = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(rightParen);
            rightParen.parser();
            // Stmt
            Stmt bodyStmt = new Stmt(GrammarType.Stmt,this.getIndex(),this.getTokens());
            this.addChild(bodyStmt);
            bodyStmt.parser();
        } else if(this.peekToken(0).getLexeme().equals("break") ||
                  this.peekToken(0).getLexeme().equals("continue")) {

            ConstToken jumpToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(jumpToken);
            jumpToken.parser();
            // ';'
            if(!this.peekToken(0).getLexeme().equals(";")) {
                frontend.Error error = new frontend.Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                this.printToError(error);
            }else {
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
                semicolon.parser();
            }
        } else if(this.peekToken(0).getLexeme().equals("return")) {
            ConstToken returnToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(returnToken);
            returnToken.parser();
            // [Exp]
            if(!this.peekToken(0).getLexeme().equals(";")) {
                Exp returnExp = new Exp(GrammarType.Exp,this.getIndex(),this.getTokens());
                this.addChild(returnExp);
                returnExp.parser();
            }
            // ';'
            if(!this.peekToken(0).getLexeme().equals(";")) {
                Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                this.printToError(error);
            }else {
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
                semicolon.parser();
            }
        } else if (this.peekToken(0).getLexeme().equals("printf")) {
            ConstToken printfToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(printfToken);
            printfToken.parser();
            // '('
            ConstToken leftParen = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(leftParen);
            leftParen.parser();
            // StringConst
            ConstToken stringConst = new ConstToken(GrammarType.StringConst,this.getIndex(),this.getTokens());
            this.addChild(stringConst);
            stringConst.parser();
            // {',' Exp}
            while (this.peekToken(0).getLexeme().equals(",")) {
                // ','
                ConstToken comma = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
                this.addChild(comma);
                comma.parser();
                // Exp
                Exp exp = new Exp(GrammarType.Exp,this.getIndex(),this.getTokens());
                this.addChild(exp);
                exp.parser();
            }
            // ')'
            if(!this.peekToken(0).getLexeme().equals(")")){
                frontend.Error error = new frontend.Error(Error.ErrorType.j, this.peekToken(-1).getLine(),"j");
                this.printToError(error);
            }else {
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(rightParen);
                rightParen.parser();
            }
            // ';'
            if(!this.peekToken(0).getLexeme().equals(";")) {
                Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                this.printToError(error);
            }else {
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
                semicolon.parser();
            }
        }else{
            int i=0;
            boolean flag=false;
            while (!this.peekToken(i).getLexeme().equals(";")){
                if(this.peekToken(i).getLexeme().equals("=")){
                    flag=true;
                    break;
                }
                i++;
            }
            if(flag) {
                // LVal '=' Exp ';'
                LVal lVal = new LVal(GrammarType.LVal,this.getIndex(),this.getTokens());
                this.addChild(lVal);
                lVal.parser();
                // '='
                ConstToken assignToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
                this.addChild(assignToken);
                assignToken.parser();
                // Exp
                Exp exp = new Exp(GrammarType.Exp,this.getIndex(),this.getTokens());
                this.addChild(exp);
                exp.parser();
                // ';'
                if(!this.peekToken(0).getLexeme().equals(";")) {
                    Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                    this.printToError(error);
                }else {
                    ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                    this.addChild(semicolon);
                    semicolon.parser();
                }
            }else if(this.peekToken(0).getLexeme().equals(";")) {
                ConstToken semicolon = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
                this.addChild(semicolon);
                semicolon.parser();
            } else {
                // [Exp] ';'
                Exp exp = new Exp(GrammarType.Exp,this.getIndex(),this.getTokens());
                this.addChild(exp);
                exp.parser();

                // ';'
                if(!this.peekToken(0).getLexeme().equals(";")) {
                    Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                    this.printToError(error);
                }else {
                    ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                    this.addChild(semicolon);
                    semicolon.parser();
                }
            }
        }
        this.printTypeToFile();// Stmt
        Node parent = this.getParent();
        parent.setIndex(this.getIndex());
    }
}
