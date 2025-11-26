package midend.Visit.Exp;

import frontend.Parser.Exp.EqExp;
import frontend.Parser.Exp.LAndExp;
import midend.LLVM.Instruction.BranchInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class VisitorLAndExp {
    /**
     * @param lAndExp     条件表达式
     * @param ifBlock     条件为真要跳转的基本块
     * @param nextOrBlock 条件为假要跳转的基本块
     * @return 条件表达式的值(cmp指令结果)
     */
    public static IrValue LLVMVisitLAndExp(LAndExp lAndExp, IrBasicBlock ifBlock, IrBasicBlock nextOrBlock) {
        ArrayList<EqExp> eqExps = lAndExp.GetEqExps();

        for (int i = 0; i < eqExps.size() - 1; i++) {
            IrBasicBlock nextEqBlock = IrBuilder.GetNewBasicBlockIr();// 下一个EqExp的基本块
            IrValue eqValue = VisitorEqExp.LLVMVisitEqExp(eqExps.get(i));

            // 处理短路与逻辑，如果eqValue为真则跳转到下一个EqExp的基本块，否则跳转到整个LAndExp的下一个基本块
            BranchInstr branchInstr = IrBuilder.GetNewBranchInstr(eqValue, nextEqBlock, nextOrBlock);

            IrBuilder.SetCurrentBasicBlock(nextEqBlock);
        }
        IrValue eqValue = VisitorEqExp.LLVMVisitEqExp(eqExps.get(eqExps.size() - 1));
        BranchInstr branchInstr = IrBuilder.GetNewBranchInstr(eqValue, ifBlock, nextOrBlock);
        return eqValue;
    }
}
