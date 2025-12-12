package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Const.IrConstIntArray;
import midend.LLVM.Const.IrConstant;
import midend.LLVM.Instruction.AllocateInstruction;
import midend.LLVM.Instruction.GepInstr;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.StoreInstr;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.use.IrUse;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;

import java.util.*;

public class GlobalVariableLocalization {

    public void run(IrModule module) {
        Map<IrGlobalValue, Set<IrFunction>> globalUsers = new HashMap<>();

        // 1. 统计使用者
        for (IrFunction func : module.getFunctions()) {
            for (IrBasicBlock bb : func.getBasicBlocks()) {
                for (Instruction inst : bb.getInstructions()) {
                    for (IrValue op : inst.getUseValues()) {
                        if (op instanceof IrGlobalValue) {
                            IrGlobalValue g = (IrGlobalValue) op;
                            globalUsers.computeIfAbsent(g, k -> new HashSet<>()).add(func);
                        }
                    }
                }
            }
        }

        // 2. 局部化
        for (Map.Entry<IrGlobalValue, Set<IrFunction>> entry : globalUsers.entrySet()) {
            IrGlobalValue global = entry.getKey();
            Set<IrFunction> users = entry.getValue();

            if (users.size() == 1) {
                IrFunction func = users.iterator().next();
                // 仅对 main 函数进行局部化，防止破坏非 main 函数中全局变量的静态语义
                if (func.irName.equals("@main") || func.irName.equals("main")) {
                    localize(global, func);
                }
            }
        }
    }

    private void localize(IrGlobalValue global, IrFunction func) {
        IrBasicBlock entryBlock = func.getEntryBlock();

        // 1. 创建 Alloca
        IrType contentType = ((IrPointer) global.irType).targetType;
        AllocateInstruction alloca = new AllocateInstruction(global.irName + "_local", contentType);
        entryBlock.addInstructionFirst(alloca);

        // 2. 初始化
        initializeLocalVariable(alloca, global.getInitial(), entryBlock);

        // 3. 替换使用
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                boolean used = false;
                for (IrValue op : inst.getUseValues()) {
                    if (op == global) {
                        used = true;
                        break;
                    }
                }
                if (used) {
                    inst.replaceUse(global, alloca);
                    // 手动维护 Use-Def 链
                    alloca.addUse(new IrUse(inst, alloca));
                }
            }
        }
    }

    private void initializeLocalVariable(AllocateInstruction alloca, IrConstant initVal, IrBasicBlock block) {
        // 插入到 alloca 之后
        int insertIdx = block.getInstructions().indexOf(alloca) + 1;

        if (initVal instanceof IrConstInt) {
            StoreInstr store = new StoreInstr(initVal, alloca);
            block.getInstructions().add(insertIdx, store);
            store.setParentBasicBlock(block);
        } else if (initVal instanceof IrConstIntArray) {
            IrConstIntArray arrayConst = (IrConstIntArray) initVal;
            ArrayList<IrConstant> elements = arrayConst.getArray();
            int totalSize = arrayConst.irType.arraySize;

            for (int i = 0; i < totalSize; i++) {
                IrConstant val;
                if (elements != null && i < elements.size()) {
                    val = elements.get(i);
                } else {
                    val = new IrConstInt(0);
                }

                GepInstr gep = new GepInstr(alloca, new IrConstInt(i));
                block.getInstructions().add(insertIdx++, gep);
                gep.setParentBasicBlock(block);

                StoreInstr store = new StoreInstr(val, gep);
                block.getInstructions().add(insertIdx++, store);
                store.setParentBasicBlock(block);
            }
        }
    }
}