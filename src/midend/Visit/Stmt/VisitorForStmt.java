package midend.Visit.Stmt;

import frontend.Parser.Exp.Exp;
import frontend.Parser.Stmt.Cond;
import frontend.Parser.Stmt.ForStmt;
import frontend.Parser.Stmt.LVal;
import frontend.Parser.Stmt.Stmt;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.JumpInstr;
import midend.LLVM.Instruction.StoreInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrLoop;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;
import midend.Visit.Exp.VisitorCond;
import midend.Visit.Exp.VisitorExp;

import java.util.ArrayList;

public class VisitorForStmt {
    public static void VisitStmt(ArrayList<ForStmt> forStmts) {
        if (!forStmts.isEmpty()) {
            for (ForStmt forStmt : forStmts) {
                VisitorLVal.VisitLVals(forStmt.getLVals(), true);
            }
        }
    }

    public static void LLVMVisitForStmt(Stmt stmt) {
        IrBasicBlock condBlock = IrBuilder.GetNewBasicBlockIr();
        IrBasicBlock bodyBlock = IrBuilder.GetNewBasicBlockIr();
        IrBasicBlock stepBlock = IrBuilder.GetNewBasicBlockIr();
        IrBasicBlock afterBlock = IrBuilder.GetNewBasicBlockIr();

        // loop入栈 (方便 handle break/continue)
        IrBuilder.LoopEnter(new IrLoop(condBlock, bodyBlock, stepBlock, afterBlock));

        // 1. 处理for的初始化部分
        ForStmt forInitStmt = stmt.GetForInitStmt();
        if (forInitStmt != null) {
            LLVMVisitForTopStmt(forInitStmt);
        }
        // 初始化完成后跳转到条件块
        IrBuilder.GetNewJumpInstr(condBlock);

        // 2. 处理条件部分
        IrBuilder.SetCurrentBasicBlock(condBlock);
        Cond forCond = stmt.GetForCond();
        if (forCond != null) {
            // 如果有条件，VisitorCond 负责生成 br true -> body, br false -> after
            VisitorCond.LLVMVisitCond(forCond, bodyBlock, afterBlock);
        } else {
            // 如果没条件 (for(;;))，无条件跳转到 body
            IrBuilder.GetNewJumpInstr(bodyBlock);
        }

        // 3. 处理循环体部分
        IrBuilder.SetCurrentBasicBlock(bodyBlock);
        VisitorStmt.LLVMVisitStmt(stmt.GetForStmtChildAsStmt());

        // 检查当前块是否已经由 break/continue/return 结束
        // 如果没有结束，说明是自然执行完循环体，需要跳去 stepBlock
        if (!IrBuilder.getCurrentBasicBlock().hasTerminator()) {
            IrBuilder.GetNewJumpInstr(stepBlock);
        }

        // 4. 处理步进部分
        IrBuilder.SetCurrentBasicBlock(stepBlock);
        ForStmt forStepStmt = stmt.GetForStepStmt();
        if (forStepStmt != null) {
            LLVMVisitForTopStmt(forStepStmt);
        }
        // Step 执行完后，跳回 Condition 进行下一次判断
        IrBuilder.GetNewJumpInstr(condBlock);

        IrBuilder.LoopExit();

        // 5. 处理循环结束后的部分
        IrBuilder.SetCurrentBasicBlock(afterBlock);
    }

    private static void LLVMVisitForTopStmt(ForStmt forInitStmt) {
        ArrayList<LVal> lVals = forInitStmt.getLVals();
        ArrayList<Exp> exps = forInitStmt.getExps();
        for (int i = 0; i < lVals.size(); i++) {
            IrValue irLVal = VisitorLVal.LLVMVisitLVal(lVals.get(i), true);
            IrValue irExp = VisitorExp.LLVMVisitExp(exps.get(i));
            irExp = IrType.convertValueToType(irExp, irLVal.irType);
            StoreInstr storeInstr = IrBuilder.GetNewStoreInstrByValueAndAddress(irExp, irLVal);
        }
    }
}