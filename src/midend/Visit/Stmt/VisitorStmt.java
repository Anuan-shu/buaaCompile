package midend.Visit.Stmt;

import frontend.Parser.Stmt.Stmt;
import midend.LLVM.Instruction.StoreInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrValue;
import midend.Visit.Exp.VisitorExp;
import midend.Visit.MainFuncDef.VisitorBlock;

public class VisitorStmt {
    public static boolean VisitStmt(Stmt stmt,boolean isNeedReturn){
        //语句 Stmt → LVal '=' Exp ';' // h
        //| [Exp] ';'
        //| Block
        //| 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
        //| 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt // h
        //| 'break' ';' | 'continue' ';' // m
        //| 'return' [Exp] ';' // f
        //| 'printf''('StringConst {','Exp}')'';' // l
        if(stmt.isBlock()){
            VisitorBlock.VisitBlock(stmt.GetChildAsBlock(),isNeedReturn,false);
        }else if(stmt.isIfStmt()){
            VisitorStmt.VisitStmt(stmt.GetIfStmtChildAsStmt(),isNeedReturn);
            if(stmt.HasElseStmt()){
                VisitorStmt.VisitStmt(stmt.GetElseStmtChildAsStmt(),isNeedReturn);
            }
        }else if(stmt.isForStmt()){
            VisitorForStmt.VisitStmt(stmt.GetChildAsForStmt());
            VisitorStmt.VisitStmt(stmt.GetForStmtChildAsStmt(),isNeedReturn);
        }else if(stmt.isLVal()){
            VisitorLVal.VisitLVal(stmt.GetChildAsLVal(), true);

            //处理等号右边的表达式
            VisitorExp.VisitExp(stmt.GetLValRExpChildAsExp());
        }else if(stmt.isBreakContinue()){
            VisitorJump.VisitJump(stmt.isForBody(), stmt.GetJumpLineNumber());
        }
        else if(stmt.isReturn()){
            return VisitorReturn.VisitReturn(stmt,isNeedReturn);
        }else if(stmt.isPrintf()){
            VisitorPrintf.VisitPrint(stmt);
        }else if(stmt.isExp()){
            VisitorExp.VisitExp(stmt.GetExpChildAsExp());
        }
        return false;
    }

    public static void LLVMVisitStmt(Stmt stmt) {
        //语句 Stmt → LVal '=' Exp ';' // h
        //| [Exp] ';'
        //| Block
        //| 'if' '(' Cond ')' Stmt [ 'else' Stmt ]
        //| 'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt // h
        //| 'break' ';' | 'continue' ';' // m
        //| 'return' [Exp] ';' // f
        //| 'printf''('StringConst {','Exp}')'';' // l
        if(stmt.isBlock()){
            VisitorBlock.LLVMVisitBlock(stmt.GetChildAsBlock());
        }else if(stmt.isIfStmt()){
            VisitorIfStmt.LLVMVisitIfStmt(stmt);
        }else if(stmt.isForStmt()){
            VisitorForStmt.LLVMVisitForStmt(stmt);
        }else if(stmt.isLVal()){
            IrValue lVal = VisitorLVal.LLVMVisitLVal(stmt.GetChildAsLVal(), true);

            //处理等号右边的表达式
            IrValue exp =  VisitorExp.LLVMVisitExp(stmt.GetLValRExpChildAsExp());
            exp = IrType.convertValueToType(exp, lVal.irType);
            //生成store指令
            StoreInstr storeInstr = IrBuilder.GetNewStoreInstrByValueAndAddress(exp, lVal);
        }else if(stmt.isBreakContinue()){
            VisitorJump.LLVMVisitJump(stmt);
        }
        else if(stmt.isReturn()){
            VisitorReturn.LLVMVisitReturn(stmt);
        }else if(stmt.isPrintf()){
            VisitorPrintf.LLVMVisitPrint(stmt);
        }else if(stmt.isExp()){//排除单 ; 情况
            VisitorExp.LLVMVisitExp(stmt.GetExpChildAsExp());
        }
    }
}
