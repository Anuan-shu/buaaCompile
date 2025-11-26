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
    private Node currentTypeNode=this;
    private boolean isForBody=false;
    public void setIsForBody(boolean isForBody) {
        this.isForBody = isForBody;
    }

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
            currentTypeNode = ifToken;
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
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(rightParen);
            }else {
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(rightParen);
                rightParen.parser();
            }
            // Stmt
            Stmt thenStmt = new Stmt(GrammarType.Stmt,this.getIndex(),this.getTokens());
            this.addChild(thenStmt);
            if(isForBody){
                thenStmt.setIsForBody(true);
            }
            thenStmt.parser();

            // [ 'else' Stmt ]
            if(this.peekToken(0).getLexeme().equals("else")) {
                ConstToken elseToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
                this.addChild(elseToken);
                elseToken.parser();

                Stmt elseStmt = new Stmt(GrammarType.Stmt,this.getIndex(),this.getTokens());
                this.addChild(elseStmt);
                if(isForBody){
                    elseStmt.setIsForBody(true);
                }
                elseStmt.parser();

            }
        } else if(this.peekToken(0).getLexeme().equals("{")) {
            //块语句
            Block blockStmt = new Block(GrammarType.Block,this.getIndex(),this.getTokens());
            this.addChild(blockStmt);
            if(isForBody){
                blockStmt.setIsForBody(true);
            }
            blockStmt.parser();
            currentTypeNode = blockStmt;
        }else if(this.peekToken(0).getLexeme().equals("for")) {
            //for语句
            ConstToken forToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(forToken);
            forToken.parser();
            currentTypeNode = forToken;
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
            bodyStmt.setIsForBody(true);
            this.addChild(bodyStmt);
            bodyStmt.parser();
        } else if(this.peekToken(0).getLexeme().equals("break") ||
                  this.peekToken(0).getLexeme().equals("continue")) {

            ConstToken jumpToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(jumpToken);
            jumpToken.parser();
            currentTypeNode = jumpToken;
            // ';'
            if(!this.peekToken(0).getLexeme().equals(";")) {
                Error error = new frontend.Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                this.printToError(error);
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
            }else {
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
                semicolon.parser();
            }
        } else if(this.peekToken(0).getLexeme().equals("return")) {
            ConstToken returnToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(returnToken);
            returnToken.parser();
            currentTypeNode = returnToken;
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
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
            }else {
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
                semicolon.parser();
            }
        } else if (this.peekToken(0).getLexeme().equals("printf")) {
            ConstToken printfToken = new ConstToken(GrammarType.Token,this.getIndex(),this.getTokens());
            this.addChild(printfToken);
            printfToken.parser();
            currentTypeNode = printfToken;
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
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(rightParen);
            }else {
                ConstToken rightParen = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(rightParen);
                rightParen.parser();
            }
            // ';'
            if(!this.peekToken(0).getLexeme().equals(";")) {
                Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                this.printToError(error);
                ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                this.addChild(semicolon);
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
                currentTypeNode = lVal;
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
                    ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                    this.addChild(semicolon);
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
                currentTypeNode = exp;
                // ';'
                if(!this.peekToken(0).getLexeme().equals(";")) {
                    Error error = new Error(Error.ErrorType.i,this.peekToken(-1).getLine(), "i");
                    this.printToError(error);
                    ConstToken semicolon = new ConstToken(GrammarType.Token, this.getIndex(),this.getTokens());
                    this.addChild(semicolon);
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

    public boolean isBlock() {
        return currentTypeNode.getTypeName().equals("Block");
    }

    public Block GetChildAsBlock() {
        return (Block) this.getChildren().get(0);
    }

    public boolean isIfStmt() {
        return this.getChildren().get(0).getType().equals(GrammarType.Token) &&
                this.getChildren().get(0).getToken().getLexeme().equals("if");
    }

    public Stmt GetIfStmtChildAsStmt() {
        return (Stmt) this.getChildren().get(4);
    }

    public boolean HasElseStmt() {
        return this.getChildren().size() == 7;
    }

    public Stmt GetElseStmtChildAsStmt() {
        return (Stmt) this.getChildren().get(6);
    }

    public boolean isForStmt() {
        return this.getChildren().get(0).getType().equals(GrammarType.Token) &&
                this.getChildren().get(0).getToken().getLexeme().equals("for");
    }

    public Stmt GetForStmtChildAsStmt() {
        for(Node child : this.getChildren()) {
            if(child.getType().equals(GrammarType.Stmt)) {
                return (Stmt) child;
            }
        }
        return null;
    }

    public boolean isReturn() {
        return this.getChildren().get(0).getType().equals(GrammarType.Token) &&
                this.getChildren().get(0).getToken().getLexeme().equals("return");
    }

    public boolean isLVal() {
        return currentTypeNode.getTypeName().equals("LVal");
    }

    public LVal GetChildAsLVal() {
        return (LVal) currentTypeNode;
    }

    public ArrayList<ForStmt> GetChildAsForStmt() {
        ArrayList<ForStmt> forStmts = new ArrayList<>();
        for(Node child : this.getChildren()) {
            if(child.getType().equals(GrammarType.ForStmt)) {
                forStmts.add((ForStmt) child);
            }
        }
        return forStmts;
    }

    public boolean isBreakContinue() {
        return this.getChildren().get(0).getType().equals(GrammarType.Token) &&
                (this.getChildren().get(0).getToken().getLexeme().equals("break")
                ||this.getChildren().get(0).getToken().getLexeme().equals("continue"));
    }

    public boolean isForBody() {
        return isForBody;
    }

    public int GetJumpLineNumber() {
        return this.getChildren().get(0).getToken().getLine();
    }

    public boolean ReturnHasValue() {
        return this.getChildren().get(1).getType()==GrammarType.Exp;
    }

    public Exp GetReturnExp() {
        return (Exp) this.getChildren().get(1);
    }

    public int GetReturnLine() {
        return this.getChildren().get(0).getToken().getLine();
    }

    public boolean isPrintf() {
        return this.getChildren().get(0).getType().equals(GrammarType.Token) &&
                this.getChildren().get(0).getToken().getLexeme().equals("printf");
    }


    public String GetStringConst() {
        return this.getChildren().get(2).getToken().getLexeme();
    }

    public ArrayList<Exp> GetExps() {
        ArrayList<Exp> exps = new ArrayList<>();
        for(Node child : this.getChildren()) {
            if(child.getType().equals(GrammarType.Exp)) {
                exps.add((Exp) child);
            }
        }
        return exps;
    }

    public int GetPrintLine() {
        return this.getChildren().get(0).getToken().getLine();
    }

    public boolean isExp() {
        return currentTypeNode.getTypeName().equals("Exp");
    }

    public Exp GetLValRExpChildAsExp() {
        return (Exp) this.getChildren().get(2);
    }

    public Exp GetExpChildAsExp() {
        return (Exp) currentTypeNode;
    }

    /**
    * 前提为if时使用
     * */
    public Cond GetStmtChildAsCond() {
        return (Cond) this.getChildren().get(2);
    }

    public ForStmt GetForInitStmt() {
        if(this.getChildren().get(2).getType().equals(GrammarType.ForStmt)) {
            return (ForStmt) this.getChildren().get(2);
        }
        return null;
    }

    public Cond GetForCond() {
        for(Node child : this.getChildren()) {
            if(child.getType().equals(GrammarType.Cond)) {
                return (Cond) child;
            }
        }
        return null;
    }

    public ForStmt GetForStepStmt() {
        int index = this.getChildren().size()-3;
        if(this.getChildren().get(index).getType().equals(GrammarType.ForStmt)) {
            return (ForStmt) this.getChildren().get(index);
        }
        return null;
    }

    public boolean isBreak() {
        return this.getChildren().get(0).getType().equals(GrammarType.Token) &&
                this.getChildren().get(0).getToken().getLexeme().equals("break");
    }
}
