package midend.Visit.Stmt;

import frontend.Parser.Stmt.Cond;
import frontend.Parser.Stmt.Stmt;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.JumpInstr;
import midend.LLVM.Instruction.ReturnInstr;
import midend.LLVM.Instruction.BranchInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.value.IrBasicBlock;
import midend.Visit.Exp.VisitorCond;

public class VisitorIfStmt {
    public static void LLVMVisitIfStmt(Stmt stmt) {
        Cond cond = stmt.GetStmtChildAsCond();
        Stmt ifStmt = stmt.GetIfStmtChildAsStmt();

        // if为真的基本块
        IrBasicBlock ifBlock = IrBuilder.GetNewBasicBlockIr();
        // 统一创建 afterBlock
        IrBasicBlock afterIfBlock = IrBuilder.GetNewBasicBlockIr();

        if (!stmt.HasElseStmt()) {
            // --- 没有 else 的情况 ---

            // 1. 处理条件 (Cond 会生成 br true, false)
            // False 跳转到 afterIfBlock
            VisitorCond.LLVMVisitCond(cond, ifBlock, afterIfBlock);

            // 2. 处理 if 语句块
            IrBuilder.SetCurrentBasicBlock(ifBlock);
            VisitorStmt.LLVMVisitStmt(ifStmt);

            // 只有当前块没有结束时，才跳到 afterBlock
            checkAndJump(afterIfBlock);

            // 3. 设置接下来的块
            IrBuilder.SetCurrentBasicBlock(afterIfBlock);

        } else {
            // --- 有 else 的情况 ---
            IrBasicBlock elseBlock = IrBuilder.GetNewBasicBlockIr();

            // 1. 处理条件
            // False 跳转到 elseBlock
            VisitorCond.LLVMVisitCond(cond, ifBlock, elseBlock);

            // 2. 处理 if 语句块
            IrBuilder.SetCurrentBasicBlock(ifBlock);
            VisitorStmt.LLVMVisitStmt(ifStmt);

            checkAndJump(afterIfBlock);

            // 3. 处理 else 语句块
            Stmt elseStmt = stmt.GetElseStmtChildAsStmt();
            IrBuilder.SetCurrentBasicBlock(elseBlock);
            VisitorStmt.LLVMVisitStmt(elseStmt);

            checkAndJump(afterIfBlock);

            // 4. 设置接下来的块
            IrBuilder.SetCurrentBasicBlock(afterIfBlock);
        }
    }

    // 辅助方法：检查当前块是否已经由 return/break/continue 结束
    private static void checkAndJump(IrBasicBlock targetBlock) {
        // 获取当前基本块
        IrBasicBlock currentBlock = IrBuilder.getCurrentBasicBlock();
        // 获取当前块的最后一条指令（你需要根据你的架构实现 getLastInstruction）
        Instruction lastInstr = currentBlock.getLastInstruction();
        boolean hasTerminator;
        if (lastInstr == null) {
            hasTerminator = false;
        } else {
            hasTerminator = lastInstr instanceof ReturnInstr || lastInstr instanceof JumpInstr || lastInstr instanceof BranchInstr;
        }

        if (!hasTerminator) {
            IrBuilder.GetNewJumpInstr(targetBlock);
        }
    }
}