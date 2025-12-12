package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.use.IrUse;
import midend.LLVM.use.IrUser;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;

import java.util.ArrayList;
import java.util.List;

public class SROA {

    public void run(IrModule module) {
        for (IrFunction func : module.getFunctions()) {
            if (func.getBasicBlocks().isEmpty()) continue;
            boolean changed = true;
            while (changed) {
                changed = runOnFunction(func);
            }
        }
    }

    private boolean runOnFunction(IrFunction func) {
        boolean changed = false;
        // 收集所有 Alloca 指令
        List<AllocateInstruction> candidates = new ArrayList<>();
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocateInstruction) {
                    AllocateInstruction alloca = (AllocateInstruction) inst;
                    // 只处理数组类型的 alloca
                    if (alloca.getAllocatedType().isArrayType()) {
                        candidates.add(alloca);
                    }
                }
            }
        }

        // 尝试对每个数组进行标量替换
        for (AllocateInstruction alloca : candidates) {
            if (canSROA(alloca)) {
                performSROA(func, alloca);
                changed = true;
            }
        }
        return changed;
    }

    // 检查是否可以进行 SROA
    private boolean canSROA(AllocateInstruction alloca) {
        // 必须只被 GEP 指令使用，且 GEP 的下标必须是常数
        for (IrUse use : alloca.useList) {
            IrUser user = use.GetUser();
            if (user instanceof GepInstr) {
                GepInstr gep = (GepInstr) user;
                // 检查下标是否为常数
                if (!(gep.getIndice() instanceof IrConstInt)) {
                    return false;
                }

                // 进一步检查 GEP 的使用者
                // GEP 的结果只能被 Load 或 Store 使用（作为地址）
                // 如果被传参或作为 Store 的值，则说明数组地址逃逸，不能 SROA
                for (IrUse gepUse : gep.useList) {
                    IrUser gepUser = gepUse.GetUser();
                    if (gepUser instanceof LoadInstr) {
                        // Load 是安全的
                    } else if (gepUser instanceof StoreInstr) {
                        StoreInstr store = (StoreInstr) gepUser;
                        // 必须是 store val, ptr 中的 ptr
                        if (store.getPtr() != gep) {
                            return false; // 试图把地址存出去
                        }
                    } else {
                        return false; // 其他用途（如函数传参），不能替换
                    }
                }
            } else {
                return false; // 数组指针直接被使用（非 GEP），不能替换
            }
        }
        return true;
    }

    // 执行替换
    private void performSROA(IrFunction func, AllocateInstruction alloca) {
        int size = alloca.getAllocatedType().arraySize;
        IrType elementType = IrType.INT32; // 数组元素都是 int

        // 1. 创建标量 Alloca
        AllocateInstruction[] scalarAllocas = new AllocateInstruction[size];
        IrBasicBlock entryBlock = func.getEntryBlock();

        // 将新创建的标量 Alloca 插入到 Entry Block 的最前面
        // 让 Mem2Reg 能更容易识别并提升它们
        for (int i = 0; i < size; i++) {
            AllocateInstruction scalar = new AllocateInstruction(alloca.irName + "_sroa_" + i, elementType);
            entryBlock.addInstructionFirst(scalar);
            scalarAllocas[i] = scalar;
        }

        // 2. 替换所有使用
        // 复制一份 useList，因为在遍历中会修改图结构
        List<IrUse> uses = new ArrayList<>(alloca.useList);
        for (IrUse use : uses) {
            GepInstr gep = (GepInstr) use.GetUser();
            int index = ((IrConstInt) gep.getIndice()).getValue();

            // 越界保护
            if (index < 0 || index >= size) continue;

            AllocateInstruction targetScalar = scalarAllocas[index];

            // 替换 GEP 的使用者
            List<IrUse> gepUses = new ArrayList<>(gep.useList);
            for (IrUse gepUse : gepUses) {
                Instruction user = (Instruction) gepUse.GetUser();

                if (user instanceof LoadInstr) {
                    // Load GEP -> Load Scalar
                    LoadInstr newLoad = new LoadInstr(targetScalar);
                    insertBefore(user, newLoad);
                    user.replaceAllUsesWith(newLoad);
                    user.getParent().getInstructions().remove(user); // 移除旧 Load
                } else if (user instanceof StoreInstr) {
                    // Store val, GEP -> Store val, Scalar
                    StoreInstr oldStore = (StoreInstr) user;
                    StoreInstr newStore = new StoreInstr(oldStore.getVal(), targetScalar);
                    insertBefore(user, newStore);
                    user.getParent().getInstructions().remove(user); // 移除旧 Store
                }
            }
            // 移除 GEP 指令
            gep.getParent().getInstructions().remove(gep);
        }

        // 3. 移除原数组 Alloca
        alloca.getParent().getInstructions().remove(alloca);
    }

    // 辅助方法：在指令 before 之前插入 newInst
    private void insertBefore(Instruction before, Instruction newInst) {
        IrBasicBlock bb = before.getParent();
        int index = bb.getInstructions().indexOf(before);
        bb.getInstructions().add(index, newInst);
        newInst.setParentBasicBlock(bb);
    }
}