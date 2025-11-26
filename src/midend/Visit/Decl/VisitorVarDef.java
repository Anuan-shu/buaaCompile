package midend.Visit.Decl;

import frontend.Parser.Decl.VarDef;
import frontend.Parser.Exp.Exp;
import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Const.IrConstant;
import midend.LLVM.Instruction.AllocateInstruction;
import midend.LLVM.Instruction.GepInstr;
import midend.LLVM.Instruction.StoreInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Visit.Exp.VisitorExp;

import java.util.ArrayList;

public class VisitorVarDef {
    public static void VisitVarDef(VarDef vardef) {
        //VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // b
        if (vardef == null) {
            return;
        }
        if (vardef.hasInitVal()) {
            //访问初始化值
            VisitorInitVal.VisitInitVal(vardef.GetInitVal());
        }
    }

    public static void LLVMVisitVarDef(VarDef vardef, boolean isStatic) {
        Symbol varSymbol = GlobalSymbolTable.searchSymbolByIdent(vardef.GetIdent(), vardef.GetLineNumber());
        if (isStatic) {
            if (GlobalSymbolTable.isGlobalSymbol(varSymbol)) {
                IrGlobalValue irGlobalValue = IrBuilder.GetNewIrStaticGlobalValue(varSymbol);
                varSymbol.setIrValue(irGlobalValue);
            } else {
                IrConstant initValue = IrBuilder.GetIrConstantFromSymbol(varSymbol);
                IrGlobalValue irGlobalValue = IrBuilder.GetNewIrStaticValue(initValue);
                varSymbol.setIrValue(irGlobalValue);
            }
        } else {
            if (GlobalSymbolTable.isGlobalSymbol(varSymbol)) {
                IrGlobalValue irGlobalValue = IrBuilder.GetNewIrGlobalValue(varSymbol);
                varSymbol.setIrValue(irGlobalValue);
            } else {
                AllocateInstruction allocateInstruction = IrBuilder.GetNewAllocateInstruction(varSymbol);
                if (!varSymbol.isArray()) {
                    //非数组，有初始值
                    if (vardef.hasInitVal()) {
                        Exp exp = vardef.GetInitVal().getExp();
                        IrValue irExp = VisitorExp.LLVMVisitExp(exp);

                        StoreInstr storeInstr = IrBuilder.GetNewStoreInstrByValueAndAddress(irExp, allocateInstruction);
                    }
                } else {
                    if (!vardef.hasInitVal()) {
                        varSymbol.setIrValue(allocateInstruction);
                        return;
                    }
                    ArrayList<Exp> exps = vardef.GetInitVal().getExpList();
                    for (int i = 0; i < exps.size(); i++) {
                        Exp exp = exps.get(i);
                        IrValue irExp = VisitorExp.LLVMVisitExp(exp);
                        irExp = IrType.convertValueToType(irExp, IrType.INT32);
                        GepInstr gepInstr = IrBuilder.GetNewGepInstr(allocateInstruction, new IrConstInt(i));
                        StoreInstr storeInstr = IrBuilder.GetNewStoreInstrByValueAndAddress(irExp, gepInstr);
                    }
                }
                varSymbol.setIrValue(allocateInstruction);
            }
        }
    }
}
