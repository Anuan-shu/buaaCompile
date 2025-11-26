package midend.Visit.Decl;

import frontend.Parser.Decl.ConstDef;
import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.AllocateInstruction;
import midend.LLVM.Instruction.GepInstr;
import midend.LLVM.Instruction.StoreInstr;
import midend.LLVM.IrBuilder;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;

import java.util.ArrayList;

public class VisitorConstDef {
    public static void VisitConstDef(ConstDef constDef) {
        //ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // b

    }

    public static void LLVMVisitConstDef(ConstDef constDef) {
        Symbol constSymbol = GlobalSymbolTable.searchSymbolByIdent(constDef.GetIdent());

        if(GlobalSymbolTable.isGlobalSymbol(constSymbol)){
            IrGlobalValue irGlobalValue = IrBuilder.GetNewIrGlobalValue(constSymbol);
            constSymbol.setIrValue(irGlobalValue);//关联全局常量符号与ir值
        }
        else{
            AllocateInstruction allocateInstruction = IrBuilder.GetNewAllocateInstruction(constSymbol);
            constSymbol.setIrValue(allocateInstruction);//关联局部常量符号与ir

            ArrayList<Integer>initValues = constSymbol.getInitValues();
            int size = constSymbol.getSize();
            //补全初始化
            for(int i=initValues.size(); i<size; i++){
                initValues.add(0);
            }
            if(!constSymbol.isArray()){
                //非数组
                //直接添加存储指令
                StoreInstr storeInstr = IrBuilder.GetNewStoreInstrBySymbol(constSymbol,allocateInstruction);
            }else{
                // 数组
                for(int i = 0; i < size; i++){
                    //生成Gep指令计算偏移量
                    GepInstr gepInstr = IrBuilder.GetNewGepInstr(allocateInstruction,new IrConstInt(i));

                    //获取初始值
                    IrValue initValue = new IrConstInt(initValues.get(i));

                    StoreInstr storeInstr = IrBuilder.GetNewStoreInstrByValueAndAddress(initValue,gepInstr);
                }
            }

        }
    }
}
