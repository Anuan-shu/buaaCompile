package midend.Optimization;

import midend.LLVM.Const.IrConstString;
import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static midend.LLVM.IrBuilder.GetLocalVarName;

public class FunctionInlining {
    // 内联阈值：指令数少于该值的函数才会被内联
    private static final int INLINE_THRESHOLD = 300;

    private static int inlineCount = 0;

    public void run(IrModule module) {
        boolean changed = true;
        // 限制迭代次数防止无限递归
        int maxIterations = 10;
        while (changed && maxIterations-- > 0) {
            changed = false;
            // 使用新列表以避免并发修改异常
            for (IrFunction caller : new ArrayList<>(module.getFunctions())) {
                if (caller.getBasicBlocks().isEmpty()) continue;
                if (runOnFunction(caller)) {
                    changed = true;
                }
            }
        }
    }

    private boolean runOnFunction(IrFunction caller) {
        boolean changed = false;
        ArrayList<IrBasicBlock> blocks = caller.getBasicBlocks();

        // 这种遍历方式允许我们在循环中修改 blocks 列表
        // 注意：每次内联后，i 需要重新调整或者 break
        for (int i = 0; i < blocks.size(); i++) {
            IrBasicBlock bb = blocks.get(i);
            ArrayList<Instruction> instrs = bb.getInstructions();

            for (int j = 0; j < instrs.size(); j++) {
                Instruction instr = instrs.get(j);
                if (instr instanceof CallInstr) {
                    CallInstr call = (CallInstr) instr;
                    IrFunction callee = call.getTargetFunction();

                    if (canInline(callee, caller)) {
                        performInline(caller, bb, call, callee);
                        changed = true;
                        // 内联发生了，CFG 结构已变，跳出内层循环，让外层重新扫描
                        // 如果不跳出，索引 j 可能会越界或处理错误的指令
                        return true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean canInline(IrFunction callee, IrFunction caller) {
        if (callee.getBasicBlocks().isEmpty()) return false;
        if (callee == caller) return false;

        // 只内联非递归函数
        // 检查 callee 是否调用了其他函数
        for (IrBasicBlock bb : callee.getBasicBlocks()) {
            for (Instruction i : bb.getInstructions()) {
                if (i instanceof CallInstr) {
                    CallInstr call = (CallInstr) i;
                    // 如果被调用的函数不是库函数，则 callee 不是叶子函数，不内联
                    if (!call.getTargetFunction().getBasicBlocks().isEmpty()) {
                        return false;
                    }
                }
            }
        }

        // 降低阈值，防止代码膨胀
        int instructionCount = 0;
        for (IrBasicBlock bb : callee.getBasicBlocks()) {
            instructionCount += bb.getInstructions().size();
        }
        return instructionCount < INLINE_THRESHOLD; // 调大一点以允许稍微复杂的叶子函数
    }

    private void performInline(IrFunction caller, IrBasicBlock callBlock, CallInstr callInstr, IrFunction callee) {
        // 获取本次内联的唯一 ID
        int currentId = inlineCount++;
        String cleanCallerName = caller.irName.replace("@", "").replace(".", "_");
        String suffix = "_inl_" + currentId;

        // 1. 拆分当前块 (Split Block)
        IrBasicBlock splitBlock = new IrBasicBlock(midend.LLVM.ValueType.BASIC_BLOCK, midend.LLVM.Type.IrType.BASICBLOCK,
                cleanCallerName + "_split" + suffix, caller);

        // 将 call 之后的指令移动到 splitBlock
        int callIndex = callBlock.getInstructions().indexOf(callInstr);
        List<Instruction> moveList = new ArrayList<>();
        for (int k = callIndex + 1; k < callBlock.getInstructions().size(); k++) {
            moveList.add(callBlock.getInstructions().get(k));
        }
        for (Instruction mv : moveList) {
            callBlock.getInstructions().remove(mv);
            splitBlock.addInstruction(mv);
        }

        // 2. 映射准备
        Map<IrValue, IrValue> valueMap = new HashMap<>(); // 旧值 -> 新值

        // 2.1 映射参数
        ArrayList<IrValue> args = callInstr.getParameters();
        // 注意：确保 callee 的参数列表与 call 指令的实参一一对应
        // 这里的 getParameters() 应该返回形参列表（Function 的参数）
        // 如果你的 IrFunction.getParameters() 返回的是 ArrayList<IrParameter>，需要确认兼容性
        for (int k = 0; k < args.size(); k++) {
            valueMap.put(callee.getParameters().get(k), args.get(k));
        }


        // 2.2 复制基本块
        Map<IrBasicBlock, IrBasicBlock> blockMap = new HashMap<>();
        ArrayList<IrBasicBlock> newBlocks = new ArrayList<>();


        for (IrBasicBlock oldBB : callee.getBasicBlocks()) {
            String cleanOldName = oldBB.irName.replace("@", "").replace(".", "_");
            IrBasicBlock newBB = new IrBasicBlock(oldBB.valueType, oldBB.irType,
                    cleanOldName + suffix, caller);
            blockMap.put(oldBB, newBB);
            newBlocks.add(newBB);
            valueMap.put(oldBB, newBB); // Label 映射很重要，用于 Branch/Jump
        }

        caller.getBasicBlocks().addAll(newBlocks);
        caller.getBasicBlocks().add(splitBlock);

        // 2.3 复制指令 (第一轮：创建对象)
        List<ReturnInstr> returns = new ArrayList<>();
        // 暂存所有新旧 Phi 的对应关系，稍后填充
        Map<PhiInstr, PhiInstr> phiMap = new HashMap<>();

        for (IrBasicBlock oldBB : callee.getBasicBlocks()) {
            IrBasicBlock newBB = blockMap.get(oldBB);
            for (Instruction oldInst : oldBB.getInstructions()) {
                Instruction newInst = cloneInstructionShallow(oldInst, valueMap, newBB);

                if (newInst instanceof ReturnInstr) {
                    returns.add((ReturnInstr) newInst);
                } else {
                    newBB.addInstruction(newInst);
                    valueMap.put(oldInst, newInst);

                    if (oldInst instanceof PhiInstr) {
                        phiMap.put((PhiInstr) oldInst, (PhiInstr) newInst);
                    }
                }
            }
        }

        // 2.4 填充 Phi 指令的 Incoming (第二轮)
        // 必须等所有块和指令都创建好后才能做，因为可能有后向边
        for (Map.Entry<PhiInstr, PhiInstr> entry : phiMap.entrySet()) {
            PhiInstr oldPhi = entry.getKey();
            PhiInstr newPhi = entry.getValue();

            for (int k = 0; k < oldPhi.getIncomingValues().size(); k++) {
                IrValue oldVal = oldPhi.getIncomingValues().get(k);
                IrBasicBlock oldBlock = oldPhi.getIncomingBlocks().get(k);

                IrValue newVal = getMappedValue(oldVal, valueMap);
                IrBasicBlock newBlock = blockMap.get(oldBlock);

                newPhi.addIncoming(newVal, newBlock);
            }
            // 复制 originalAlloca 属性（如果还在 Mem2Reg 阶段的话）
            if (oldPhi.getOriginalAlloca() != null) {
                // 如果 Alloca 也被复制了，这里应该指向新的 Alloca
                Instruction newAlloca = (Instruction) valueMap.get(oldPhi.getOriginalAlloca());
                if (newAlloca instanceof AllocateInstruction) {
                    newPhi.setOriginalAlloca((AllocateInstruction) newAlloca);
                }
            }
        }

        // 3. 连接控制流

        // 3.1 CallBlock -> Callee Entry
        IrBasicBlock calleeEntry = blockMap.get(callee.getEntryBlock());
        callBlock.addInstruction(new JumpInstr(calleeEntry));

        // 3.2 处理 Return -> SplitBlock
        callBlock.getInstructions().remove(callInstr); // 删除原 Call

        IrValue retValue = null;

        if (!returns.isEmpty()) {
            if (!callInstr.irType.isVoid()) {
                if (returns.size() == 1) {
                    IrValue oldRet = returns.get(0).getReturnValue();
                    retValue = getMappedValue(oldRet, valueMap);
                } else {
                    // 多个 Return，插入 Phi
                    PhiInstr phi = new PhiInstr(callInstr.irType, splitBlock);
                    for (ReturnInstr ret : returns) {
                        IrValue oldRet = ret.getReturnValue();
                        IrValue val = getMappedValue(oldRet, valueMap);
                        // ret 的 parent 是已经 clone 后的块
                        phi.addIncoming(val, (IrBasicBlock) ret.getParent());
                    }
                    splitBlock.addInstructionFirst(phi);
                    retValue = phi;
                }
            }

            // 所有 return 块跳转到 splitBlock
            for (ReturnInstr ret : returns) {
                IrBasicBlock retBlock = (IrBasicBlock) ret.getParent();
                // 移除 return 指令本身（因为它不产生控制流了，我们要接管）
                // 注意：之前 clone 时 return 没有加入到 instruction list，所以不用 remove
                retBlock.addInstruction(new JumpInstr(splitBlock));
            }
        } else {
            // Void function 可能没有显式 return，或者是 CFG 末端的块
            for (IrBasicBlock newBB : newBlocks) {
                if (!newBB.hasTerminator()) {
                    newBB.addInstruction(new JumpInstr(splitBlock));
                }
            }
        }

        // 4. 替换 Call 的使用者
        if (retValue != null) {
            callInstr.replaceAllUsesWith(retValue);
        }
    }

    private IrValue getMappedValue(IrValue old, Map<IrValue, IrValue> map) {
        if (old instanceof midend.LLVM.Const.IrConstant ||
                old instanceof midend.LLVM.value.IrGlobalValue) {
            return old;
        }
        return map.getOrDefault(old, old);
    }

    // 浅克隆：只创建对象和映射操作数，不处理 Phi 的 incoming
    private Instruction cloneInstructionShallow(Instruction old, Map<IrValue, IrValue> map, IrBasicBlock newParent) {
        Instruction newInst = null;

        if (old instanceof AluInst) {
            AluInst a = (AluInst) old;
            newInst = new AluInst(a.getOpDire(),
                    getMappedValue(a.getLeft(), map),
                    getMappedValue(a.getRight(), map));
        } else if (old instanceof CmpInstr) {
            CmpInstr c = (CmpInstr) old;
            newInst = new CmpInstr(c.getOpDire(),
                    getMappedValue(c.getLeft(), map),
                    getMappedValue(c.getRight(), map));
        } else if (old instanceof LoadInstr) {
            LoadInstr l = (LoadInstr) old;
            newInst = new LoadInstr(getMappedValue(l.getPtr(), map));
        } else if (old instanceof StoreInstr) {
            StoreInstr s = (StoreInstr) old;
            newInst = new StoreInstr(getMappedValue(s.getVal(), map),
                    getMappedValue(s.getPtr(), map));
        } else if (old instanceof BranchInstr) {
            BranchInstr b = (BranchInstr) old;
            if (b.getCond() != null) {
                newInst = new BranchInstr(getMappedValue(b.getCond(), map),
                        (IrBasicBlock) getMappedValue(b.getTrueBlock(), map),
                        (IrBasicBlock) getMappedValue(b.getFalseBlock(), map));
            }
        } else if (old instanceof JumpInstr) {
            JumpInstr j = (JumpInstr) old;
            newInst = new JumpInstr((IrBasicBlock) getMappedValue(j.getTargetBlock(), map));
        } else if (old instanceof CallInstr) {
            CallInstr c = (CallInstr) old;
            ArrayList<IrValue> newParams = new ArrayList<>();
            for (IrValue p : c.getParameters()) {
                newParams.add(getMappedValue(p, map));
            }
            newInst = new CallInstr(c.getTargetFunction(), newParams); // 目标函数通常不变
        } else if (old instanceof GepInstr) {
            GepInstr g = (GepInstr) old;
            newInst = new GepInstr(getMappedValue(g.getPtr(), map),
                    getMappedValue(g.getIndice(), map));
        } else if (old instanceof ZextInstr) {
            ZextInstr z = (ZextInstr) old;
            newInst = new ZextInstr(getMappedValue(z.getOperand(0), map), z.irType);
        } else if (old instanceof TruncInstr) {
            TruncInstr t = (TruncInstr) old;
            newInst = new TruncInstr(getMappedValue(t.getOperand(0), map), t.irType);
        } else if (old instanceof PrintIntInstr) {
            PrintIntInstr p = (PrintIntInstr) old;
            newInst = new PrintIntInstr(getMappedValue(p.getPrintValue(), map));
        } else if (old instanceof PrintStrInstr) {
            PrintStrInstr p = (PrintStrInstr) old;
            // 字符串常量本身是 GlobalValue，getMappedValue 会直接返回原值
            newInst = new PrintStrInstr((IrConstString) getMappedValue(p.getPrintValue(), map));
        } else if (old instanceof AllocateInstruction) {
            AllocateInstruction a = (AllocateInstruction) old;
            newInst = new AllocateInstruction(GetLocalVarName(), a.getAllocatedType());
        } else if (old instanceof PhiInstr) {
            // Phi 特殊处理：只创建对象，不填充 Incoming
            PhiInstr p = (PhiInstr) old;
            newInst = new PhiInstr(p.irType, newParent);
        } else if (old instanceof ReturnInstr) {
            ReturnInstr r = (ReturnInstr) old;
            IrValue v = r.isReturnVoid() ? null : getMappedValue(r.getReturnValue(), map);
            newInst = new ReturnInstr(v);
            newInst.setParentBasicBlock(newParent);
            return newInst;
        }

        if (newInst == null) {
            throw new RuntimeException("FunctionInlining: Unhandled instruction type: " + old.getClass().getSimpleName());
        }

        newInst.setParentBasicBlock(newParent);
        return newInst;
    }
}