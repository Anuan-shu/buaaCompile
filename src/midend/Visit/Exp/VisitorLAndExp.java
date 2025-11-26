package midend.Visit.Exp;

import frontend.Parser.Exp.EqExp;
import frontend.Parser.Exp.LAndExp;
import midend.LLVM.Instruction.BranchInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class VisitorLAndExp {
    public static IrValue LLVMVisitLAndExp(LAndExp lAndExp, IrBasicBlock ifBlock, IrBasicBlock nextOrBlock) {
        ArrayList<EqExp> eqExps = lAndExp.GetEqExps();

        for (int i=0;i< eqExps.size()-1;i++){
            IrBasicBlock nextEqBlock = IrBuilder.GetNewBasicBlockIr();
            IrValue eqValue = VisitorEqExp.LLVMVisitEqExp(eqExps.get(i));

            // 处理短路与逻辑
            BranchInstr branchInstr = IrBuilder.GetNewBranchInstr(eqValue, nextEqBlock, nextOrBlock);

            IrBuilder.SetCurrentBasicBlock(nextEqBlock);
        }
        IrValue eqValue = VisitorEqExp.LLVMVisitEqExp(eqExps.get(eqExps.size()-1));
        BranchInstr branchInstr = IrBuilder.GetNewBranchInstr(eqValue, ifBlock, nextOrBlock);
        return eqValue;
    }
}
