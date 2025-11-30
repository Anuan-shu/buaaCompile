package midend.SSA;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.AllocateInstruction;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.LoadInstr;
import midend.LLVM.Instruction.StoreInstr;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrPointer;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;

import java.util.*;

public class Mem2Reg {
    private IrModule module;
    // 记录每个 Alloca 当前的版本栈
    private Map<AllocateInstruction, Stack<IrValue>> varStacks = new HashMap<>();
    // 记录待删除的指令，统一最后删除，防止遍历时修改集合报错
    private Set<Instruction> deadInstructions = new HashSet<>();

    public void run(IrModule module) {
        this.module = module;
        for (IrFunction func : module.getFunctions()) {
            if (func.getBasicBlocks().isEmpty()) continue;
            runOnFunction(func);
        }
    }

    private void runOnFunction(IrFunction func) {
        // 清理状态
        varStacks.clear();
        deadInstructions.clear();

        // 1. 构建支配树
        DominatorTree domTree = new DominatorTree(func);

        // 2. 收集可提升的 alloca (排除数组)
        List<AllocateInstruction> allocas = collectPromotableAllocas(func);

        // 3. 插入 Phi 节点
        insertPhiNodes(func, allocas, domTree);

        // 4. 变量重命名 (初始化栈)
        for (AllocateInstruction alloca : allocas) {
            varStacks.put(alloca, new Stack<>());
            // 初始状态压入 0 (处理未初始化变量的情况)
            varStacks.get(alloca).push(new IrConstInt(0));
        }

        rename(func.getEntryBlock(), domTree);

        // 5. 清理垃圾 (删除被标记的 load/store/alloca)
        removePromotedInstructions(func);
    }

    /**
     * 收集可以被提升为寄存器的 Alloca 指令
     * 条件：必须是标量类型 (i32, ptr)，不能是数组或结构体
     */
    private List<AllocateInstruction> collectPromotableAllocas(IrFunction func) {
        List<AllocateInstruction> list = new ArrayList<>();
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr instanceof AllocateInstruction) {
                    AllocateInstruction alloca = (AllocateInstruction) instr;
                    IrType pointeeType = ((IrPointer) alloca.irType).targetType;
                    // 如果不是数组类型，就可以提升
                    if (!pointeeType.isArrayType()) {
                        list.add(alloca);
                    }
                }
            }
        }
        return list;
    }

    /**
     * 获取某个 Alloca 指令被定义（Store）的所有基本块
     */
    private Set<IrBasicBlock> getDefBlocks(AllocateInstruction alloca) {
        Set<IrBasicBlock> set = new HashSet<>();
        // 遍历整个函数寻找使用该 alloca 的 store 指令
        // 优化：如果你的 IrValue 维护了 useList，可以直接遍历 useList

        IrFunction func = (IrFunction) alloca.getParent().getParent(); // 获取所属函数
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction instr : bb.getInstructions()) {
                if (instr instanceof StoreInstr) {
                    StoreInstr store = (StoreInstr) instr;
                    // Store 的第二个操作数是指针 (ptr)
                    // 如果 store 的目标是该 alloca
                    if (store.getPtr() == alloca || store.getPtr().equals(alloca)) {
                        set.add(bb);
                    }
                }
            }
        }
        return set;
    }

    private void insertPhiNodes(IrFunction func, List<AllocateInstruction> allocas, DominatorTree domTree) {
        for (AllocateInstruction alloca : allocas) {
            Set<IrBasicBlock> defBlocks = getDefBlocks(alloca);
            Set<IrBasicBlock> liveSet = new HashSet<>();
            Queue<IrBasicBlock> workList = new LinkedList<>(defBlocks);

            while (!workList.isEmpty()) {
                IrBasicBlock block = workList.poll();
                // 遍历支配边界
                for (IrBasicBlock dfBlock : domTree.getDominanceFrontier(block)) {
                    if (!liveSet.contains(dfBlock)) {
                        // 插入 Phi
                        // 类型应该是 alloca 指向的类型 (比如 alloca i32 -> phi i32)
                        PhiInstr phi = new PhiInstr(((IrPointer) alloca.irType).targetType, dfBlock);
                        phi.setOriginalAlloca(alloca);

                        dfBlock.addInstructionFirst(phi); // 插在块头

                        liveSet.add(dfBlock);
                        workList.add(dfBlock);
                    }
                }
            }
        }
    }

    private void rename(IrBasicBlock bb, DominatorTree domTree) {
        Map<AllocateInstruction, Integer> pushCount = new HashMap<>();

        // 1. 处理 Phi 定义 (Phi 指令在块头，并行执行)
        for (Instruction instr : bb.getInstructions()) {
            if (instr instanceof PhiInstr) {
                PhiInstr phi = (PhiInstr) instr;
                AllocateInstruction alloca = phi.getOriginalAlloca();
                if (alloca != null) {
                    varStacks.get(alloca).push(phi);
                    pushCount.merge(alloca, 1, Integer::sum);
                }
            }
        }

        // 2. 处理 Load / Store
        for (Instruction instr : bb.getInstructions()) {
            if (instr instanceof LoadInstr) {
                LoadInstr load = (LoadInstr) instr;
                if (load.getPtr() instanceof AllocateInstruction) {
                    AllocateInstruction alloca = (AllocateInstruction) load.getPtr();
                    if (varStacks.containsKey(alloca)) {
                        IrValue currVal = varStacks.get(alloca).peek();

                        // 将 load 的结果替换为 currVal
                        load.replaceAllUsesWith(currVal);

                        deadInstructions.add(load); // 标记删除
                    }
                }
            } else if (instr instanceof StoreInstr) {
                StoreInstr store = (StoreInstr) instr;
                if (store.getPtr() instanceof AllocateInstruction) {
                    AllocateInstruction alloca = (AllocateInstruction) store.getPtr();
                    if (varStacks.containsKey(alloca)) {
                        varStacks.get(alloca).push(store.getVal());
                        pushCount.merge(alloca, 1, Integer::sum);

                        deadInstructions.add(store); // 标记删除
                    }
                }
            }
        }

        // 3. 填充后继块的 Phi 参数
        for (IrBasicBlock succ : bb.getSuccessors()) {
            for (Instruction instr : succ.getInstructions()) {
                if (instr instanceof PhiInstr) {
                    PhiInstr phi = (PhiInstr) instr;
                    AllocateInstruction alloca = phi.getOriginalAlloca();
                    if (alloca != null && varStacks.containsKey(alloca) && !varStacks.get(alloca).isEmpty()) {
                        phi.addIncoming(varStacks.get(alloca).peek(), bb);
                    }
                }
            }
        }

        // 4. 递归
        for (IrBasicBlock child : domTree.getChildren(bb)) {
            rename(child, domTree);
        }

        // 5. 回溯 (Pop Stack)
        for (Map.Entry<AllocateInstruction, Integer> entry : pushCount.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                varStacks.get(entry.getKey()).pop();
            }
        }
    }

    /**
     * 物理删除所有被标记的指令
     */
    private void removePromotedInstructions(IrFunction func) {
        // 先删除 deadInstructions 中的 load/store
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            // 使用 removeIf 安全删除
            bb.getInstructions().removeIf(instr -> deadInstructions.contains(instr));
        }

        // 再删除所有被提升的 alloca (因为它们已经变成寄存器了)
        // 遍历 varStacks 的 keySet 即可知道哪些 alloca 被提升了
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            bb.getInstructions().removeIf(instr -> instr instanceof AllocateInstruction && varStacks.containsKey(instr));
        }
    }
}