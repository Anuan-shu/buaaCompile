package midend.LLVM;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Const.IrConstIntArray;
import midend.LLVM.Const.IrConstString;
import midend.LLVM.Const.IrConstant;
import midend.LLVM.Instruction.*;
import midend.LLVM.Type.IrLoop;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;
import midend.Symbol.Symbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class IrBuilder {
    private static IrModule irModule = new IrModule(ValueType.MOUDLE, IrType.MODULE, "IRModule");
    private static IrFunction currentFunction = null;
    private static IrBasicBlock currentBasicBlock = null;
    private static Stack<IrLoop> loopStack = new Stack<>();

    private static int basicBlockNum = 0;
    private static int globalVarNum = 0;
    private static int stringConstNum = 0;
    private static final HashMap<IrFunction, Integer> localVarNum = new HashMap<>();

    public static IrModule getIrModule() {
        return irModule;
    }

    public static IrBasicBlock getCurrentBasicBlock() {
        return currentBasicBlock;
    }

    public static void SetCurrentBasicBlock(IrBasicBlock irBasicBlock) {
        currentBasicBlock = irBasicBlock;
    }

    //传入函数名，ValueType.FUNCTION，IrType.INT32/VOID
    public static IrFunction GetNewIrFunction(String main, ValueType valueType, IrType irType) {
        main = GetFuncName(main);
        // 创建函数
        IrFunction irFunction = new IrFunction(valueType, irType, main);
        irModule.addFunction(irFunction);
        currentFunction = irFunction;

        // 创建第一个基本块
        IrBasicBlock irBasicBlock = GetNewBasicBlockIr();
        currentBasicBlock = irBasicBlock;

        // 初始化局部变量计数
        localVarNum.put(irFunction, 0);

        return irFunction;
    }

    public static IrBasicBlock GetNewBasicBlockIr() {
        IrBasicBlock basicBlock = new IrBasicBlock(ValueType.BASIC_BLOCK, IrType.BASICBLOCK, GetBasicBlockName(), currentFunction);
        // 添加到当前的处理中
        currentFunction.addBasicBlock(basicBlock);

        return basicBlock;
    }

    private static String GetBasicBlockName() {
        return "b_" + basicBlockNum++;
    }

    private static String GetFuncName(String indent) {
        if (indent.equals("main")) {
            return "@main";
        }
        return "@f_" + indent;
    }

    public static IrGlobalValue GetNewIrGlobalValue(Symbol constSymbol) {
        IrConstant initial = GetIrConstantFromSymbol(constSymbol);

        IrGlobalValue irGlobalValue = new IrGlobalValue(ValueType.GLOBAL_VARIABLE, new IrPointer(initial.irType), GetGlobalVarName(), initial);
        irModule.addGlobalValue(irGlobalValue);
        return irGlobalValue;
    }

    /**
     * 根据符号表中的符号（要有初始值），生成对应的IrConstant
     *
     * @param constSymbol
     * @return IrConstant或IrConstIntArray
     */
    public static IrConstant GetIrConstantFromSymbol(Symbol constSymbol) {
        ArrayList<Integer> initValues = constSymbol.getInitValues();
        if (!constSymbol.isArray()) {
            // 非数组
            if (initValues.size() == 0) {
                return new IrConstInt(0);
            } else {
                return new IrConstInt(initValues.get(0));
            }
        } else {
            ArrayList<IrConstant> constantList = new ArrayList<>();
            int size = constSymbol.getSize();
            for (Integer val : initValues) {
                constantList.add(new IrConstInt(val));
            }
            // 未初始化的部分为0
            for (int i = initValues.size(); i < size; i++) {
                constantList.add(new IrConstInt(0));
            }
            return new IrConstIntArray(constSymbol.GetSymbolName(), constantList);
        }
    }

    private static String GetGlobalVarName() {
        return "@g_" + globalVarNum++;
    }

    public static AllocateInstruction GetNewAllocateInstruction(Symbol constSymbol) {
        IrType allocatedType = constSymbol.getIrType();// 获取分配类型
        //System.out.println(constSymbol.GetSymbolName()+":" + allocatedType);
        AllocateInstruction allocateInstruction = new AllocateInstruction(GetLocalVarName(), allocatedType);

        // 添加到当前基本块
        addInstr(allocateInstruction);

        return allocateInstruction;
    }

    public static String GetLocalVarName() {
        int varNum = localVarNum.get(currentFunction);
        localVarNum.put(currentFunction, varNum + 1);
        return "%v" + varNum;
    }

    public static StoreInstr GetNewStoreInstrBySymbol(Symbol constSymbol, AllocateInstruction allocateInstr) {
        StoreInstr storeInstr = new StoreInstr(GetIrConstantFromSymbol(constSymbol), allocateInstr);
        addInstr(storeInstr);
        return storeInstr;
    }

    // 添加指令到当前基本块
    public static void addInstr(Instruction instr) {
        currentBasicBlock.addInstruction(instr);
        instr.setParentBasicBlock(currentBasicBlock);
    }

    public static GepInstr GetNewGepInstr(IrValue instruction, IrValue offset) {
        GepInstr gepInstr = new GepInstr(instruction, offset);
        addInstr(gepInstr);
        return gepInstr;
    }

    public static StoreInstr GetNewStoreInstrByValueAndAddress(IrValue initValue, IrValue address) {
        StoreInstr storeInstr = new StoreInstr(initValue, address);
        addInstr(storeInstr);
        return storeInstr;
    }

    public static LoadInstr GetNewLoadInstr(IrValue pointer) {
        LoadInstr loadInstr = new LoadInstr(pointer);
        addInstr(loadInstr);
        return loadInstr;
    }

    public static CallInstr GetNewCallInstr(IrFunction irFunction, ArrayList<IrValue> paramList) {
        CallInstr callInstr = new CallInstr(irFunction, paramList);
        addInstr(callInstr);
        return callInstr;
    }

    public static AluInst GetNewAluInst(String op, IrValue l, IrValue r) {
        AluInst aluInst = new AluInst(op, l, r);
        addInstr(aluInst);
        return aluInst;
    }

    public static CmpInstr GetNewCmpInstr(String s, IrValue left, IrValue ret) {
        CmpInstr cmpInstr = new CmpInstr(s, left, ret);
        addInstr(cmpInstr);
        return cmpInstr;
    }

    public static ZextInstr GetNewZextInstr(IrValue irValue, IrType int32) {
        ZextInstr zextInstr = new ZextInstr(irValue, int32);
        addInstr(zextInstr);
        return zextInstr;
    }

    public static ReturnInstr GetNewReturnInstr(IrValue returnValue) {
        ReturnInstr returnInstr = new ReturnInstr(returnValue);
        addInstr(returnInstr);
        return returnInstr;
    }

    public static IrValue GetNewTruncInstr(IrValue value, IrType targetType) {
        TruncInstr truncInstr = new TruncInstr(value, targetType);
        addInstr(truncInstr);
        return truncInstr;
    }

    public static BranchInstr GetNewBranchInstr(IrValue eqValue, IrBasicBlock nextEqBlock, IrBasicBlock nextOrBlock) {
        BranchInstr branchInstr = new BranchInstr(eqValue, nextEqBlock, nextOrBlock);
        addInstr(branchInstr);
        return branchInstr;
    }

    public static JumpInstr GetNewJumpInstr(IrBasicBlock ifBlock) {
        JumpInstr jumpInstr = new JumpInstr(ifBlock);
        addInstr(jumpInstr);
        return jumpInstr;
    }

    public static void LoopEnter(IrLoop irLoop) {
        loopStack.push(irLoop);
    }

    public static void LoopExit() {
        loopStack.pop();
    }

    public static IrLoop LoopPeek() {
        return loopStack.peek();
    }

    public static IrConstString GetNewIrConstString(String string) {
        // 创建字符串常量
        return irModule.GetNewIrConstString(string);
    }

    public static String GetStringConstName() {
        return "@s_" + stringConstNum++;
    }

    public static PrintIntInstr GetNewPrintIntInstr(IrValue irValue) {
        PrintIntInstr printIntInstr = new PrintIntInstr(irValue);
        addInstr(printIntInstr);
        return printIntInstr;
    }

    public static PrintStrInstr GetNewPrintStrInstr(IrConstString irConstString) {
        PrintStrInstr printStrInstr = new PrintStrInstr(irConstString);
        addInstr(printStrInstr);
        return printStrInstr;
    }

    public static IrGlobalValue GetNewIrStaticGlobalValue(Symbol varSymbol) {
        IrConstant initial = GetIrConstantFromSymbol(varSymbol);

        return GetNewIrStaticValue(initial);
    }

    public static IrGlobalValue GetNewIrStaticValue(IrConstant initValue) {
        IrGlobalValue irGlobalValue = new IrGlobalValue(ValueType.GLOBAL_VARIABLE, new IrPointer(initValue.irType), GetGlobalVarName(), initValue, true);
        irModule.addGlobalValue(irGlobalValue);
        return irGlobalValue;
    }

    public static AllocateInstruction GetNewAllocInstrByType(IrType irType) {
        AllocateInstruction allocateInstruction = new AllocateInstruction(GetLocalVarName(), irType);

        // 添加到当前基本块
        addInstr(allocateInstruction);

        return allocateInstruction;
    }

}
