package midend.Visit.Exp;

import frontend.Parser.Exp.LAndExp;
import frontend.Parser.Exp.LOrExp;
import midend.LLVM.Instruction.BranchInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class VisitorLorExp {
    public static void LLVMVisitLorExp(LOrExp lOrExp, IrBasicBlock ifBlock, IrBasicBlock afterIfBlock) {
        ArrayList<LAndExp> lAndExps = lOrExp.GetLAndExps();
        for (int i = 0; i < lAndExps.size() - 1; i++) {
            IrBasicBlock nextOrBlock = IrBuilder.GetNewBasicBlockIr();
            IrValue andValue = VisitorLAndExp.LLVMVisitLAndExp(lAndExps.get(i), ifBlock, nextOrBlock);

            //andValue = IrType.convertValueToType(andValue, IrType.INT1);
            //BranchInstr branchInstr = IrBuilder.GetNewBranchInstr(andValue, ifBlock, nextOrBlock);

            IrBuilder.SetCurrentBasicBlock(nextOrBlock);
        }
        VisitorLAndExp.LLVMVisitLAndExp(lAndExps.get(lAndExps.size() - 1), ifBlock, afterIfBlock);
    }
}
