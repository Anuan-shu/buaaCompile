package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.DeadCodeElimination;
import midend.SSA.DominatorTree;
import midend.SSA.PhiInstr;

import java.util.*;

public class LICM {

    private DominatorTree domTree;

    public void run(IrModule module) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrFunction function : module.getFunctions()) {
                if (function.getBasicBlocks().isEmpty()) continue;

                // 1. 构建支配树
                domTree = new DominatorTree(function);

                // 2. 识别自然循环
                List<Loop> loops = findLoops(function);

                // 3. 对每个循环进行 LICM
                for (Loop loop : loops) {
                    if (optimizeLoop(function, loop)) {
                        changed = true;
                    }
                }
            }
        }
    }

    // --- 循环识别 ---
    private List<Loop> findLoops(IrFunction function) {
        List<Loop> loops = new ArrayList<>();

        // 遍历所有块寻找回边 A -> B
        // 且 B 支配 A (B dom A)
        for (IrBasicBlock A : function.getBasicBlocks()) {
            for (IrBasicBlock B : A.getSuccessors()) {
                if (isDominator(B, A)) {
                    // 发现自然循环: Header = B, Latch = A
                    Loop loop = new Loop();
                    loop.header = B;
                    loop.latches.add(A); // 一个循环可能有多个 Latch

                    // 填充 Body
                    populateLoopBody(loop, A);

                    // 检查是否已经存在同 Header 的循环 (合并多 Latch 循环)
                    Loop existing = null;
                    for (Loop l : loops) {
                        if (l.header == B) {
                            existing = l;
                            break;
                        }
                    }

                    if (existing != null) {
                        existing.body.addAll(loop.body);
                        existing.latches.add(A);
                    } else {
                        loops.add(loop);
                    }
                }
            }
        }
        return loops;
    }

    // 判断 A 是否支配 B
    private boolean isDominator(IrBasicBlock A, IrBasicBlock B) {
        if (A == B) return true;
        if (domTree.getDomDepth(A) >= domTree.getDomDepth(B)) return false;

        IrBasicBlock runner = B;
        while (runner != null && runner != A) {
            runner = domTree.getIDom(runner);
        }
        return runner == A;
    }

    private void populateLoopBody(Loop loop, IrBasicBlock latch) {
        if (loop.body.contains(latch)) return;
        loop.body.add(latch);
        loop.body.add(loop.header); // Header 必在 Body 中

        Stack<IrBasicBlock> stack = new Stack<>();
        stack.push(latch);
        while (!stack.isEmpty()) {
            IrBasicBlock curr = stack.pop();
            for (IrBasicBlock pred : curr.getPredecessors()) {
                if (!loop.body.contains(pred)) {
                    loop.body.add(pred);
                    stack.push(pred);
                }
            }
        }
    }

    // --- 核心优化逻辑 ---
    private boolean optimizeLoop(IrFunction function, Loop loop) {
        // 1. 获取或创建 PreHeader
        IrBasicBlock preHeader = getOrCreatePreHeader(function, loop);
        if (preHeader == null) return false;

        boolean changed = false;
        boolean localChange = true;

        // 迭代直到没有指令能移出
        while (localChange) {
            localChange = false;
            // 收集所有候选指令
            List<Instruction> candidates = new ArrayList<>();
            for (IrBasicBlock bb : loop.body) {
                candidates.addAll(bb.getInstructions());
            }

            for (Instruction inst : candidates) {
                // 必须在循环体中
                if (!loop.body.contains(inst.getParent())) continue;

                if (isInvariant(inst, loop) && canHoist(inst, loop)) {
                    // 移动指令
                    inst.getParent().getInstructions().remove(inst);

                    // 插入到 PreHeader 末尾
                    Instruction term = preHeader.getTerminator();
                    if (term != null) {
                        int idx = preHeader.getInstructions().indexOf(term);
                        preHeader.getInstructions().add(idx, inst);
                    } else {
                        preHeader.addInstruction(inst);
                    }

                    inst.setParentBasicBlock(preHeader);

                    localChange = true;
                    changed = true;
                }
            }
        }
        return changed;
    }

    // 判断是否为循环不变量
    private boolean isInvariant(Instruction inst, Loop loop) {
        // 1. 过滤不可移动的指令
        if (inst instanceof PhiInstr) return false;
        if (inst instanceof BranchInstr || inst instanceof JumpInstr || inst instanceof ReturnInstr) return false;
        if (inst instanceof CallInstr) return false; // 除非是 pure function
        if (inst instanceof LoadInstr) return false; // 需要 Memory Alias Analysis
        if (inst instanceof StoreInstr) return false;
        if (inst instanceof AllocateInstruction) return false;
        if (inst instanceof PrintStrInstr) return false;
        if (inst instanceof PrintIntInstr) return false;

        // GepInstr 特殊处理：如果基指针是全局变量，可以考虑外提
        // 但需要检查索引是否是循环不变量
        if (inst instanceof GepInstr) {
            GepInstr gep = (GepInstr) inst;
            IrValue ptr = gep.getPtr();
            IrValue idx = gep.getIndice();

            // 基指针必须是全局变量（常量地址）
            if (!(ptr instanceof midend.LLVM.value.IrGlobalValue)) {
                // 如果基指针是另一个 GEP，检查它是否在循环外
                if (ptr instanceof Instruction) {
                    Instruction ptrInst = (Instruction) ptr;
                    if (loop.body.contains(ptrInst.getParent())) {
                        return false;
                    }
                }
            }

            // 索引必须是循环不变量
            if (idx instanceof Instruction) {
                Instruction idxInst = (Instruction) idx;
                if (loop.body.contains(idxInst.getParent())) {
                    return false;
                }
            }
            return true;
        }

        // 2. 检查所有操作数
        for (IrValue operand : getOperands(inst)) {
            // 常量或全局变量 -> Invariant
            if (operand instanceof midend.LLVM.Const.IrConstant ||
                    operand instanceof midend.LLVM.value.IrGlobalValue ||
                    operand instanceof midend.LLVM.value.IrParameter) { // 函数参数也是 invariant
                continue;
            }

            // 如果操作数是指令
            if (operand instanceof Instruction) {
                Instruction opInst = (Instruction) operand;
                // 如果定义该操作数的指令在循环内 -> Not Invariant
                if (loop.body.contains(opInst.getParent())) {
                    return false;
                }
            }
        }
        return true;
    }

    // 检查是否有副作用或异常风险
    private boolean canHoist(Instruction inst, Loop loop) {
        // 1. 除法保护: 除非除数是非零常量，否则不能提
        if (inst instanceof AluInst) {
            AluInst alu = (AluInst) inst;
            if (alu.getOp().equals("SDIV") || alu.getOp().equals("SREM")) {
                IrValue rhs = alu.getRight();
                if (rhs instanceof IrConstInt && ((IrConstInt) rhs).getValue() != 0) {
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    // --- 辅助方法 ---
    private IrBasicBlock getOrCreatePreHeader(IrFunction func, Loop loop) {
        // 寻找循环的所有外部前驱
        List<IrBasicBlock> outsidePreds = new ArrayList<>();
        for (IrBasicBlock pred : loop.header.getPredecessors()) {
            if (!loop.body.contains(pred)) {
                outsidePreds.add(pred);
            }
        }

        if (outsidePreds.isEmpty()) return null; // 死循环或不可达

        // 检查现有的唯一外部前驱是否已经是 PreHeader
        if (outsidePreds.size() == 1) {
            IrBasicBlock candidate = outsidePreds.get(0);
            // 要求 candidate 只有 Header 一个后继，且无条件跳转
            if (candidate.getSuccessors().size() == 1 && candidate.getSuccessors().get(0) == loop.header) {
                return candidate;
            }
        }

        // 创建新的 PreHeader
        if (outsidePreds.size() > 1) return null;

        // 针对单入口
        IrBasicBlock pred = outsidePreds.get(0);

        IrBasicBlock preHeader = new IrBasicBlock(ValueType.BASIC_BLOCK, IrType.BASICBLOCK, "preheader", func);

        // 插入到 Function Block List 合适位置
        int headerIdx = func.getBasicBlocks().indexOf(loop.header);
        func.getBasicBlocks().add(headerIdx, preHeader);

        // 重定向 pred -> preHeader
        Instruction term = pred.getTerminator();
        if (term instanceof BranchInstr) {
            BranchInstr br = (BranchInstr) term;
            if (br.getTrueBlock() == loop.header) br.setTrueBlock(preHeader);
            if (br.getFalseBlock() == loop.header) br.setFalseBlock(preHeader);
        } else if (term instanceof JumpInstr) {
            ((JumpInstr) term).setTargetBlock(preHeader);
        }

        // 更新前驱后继链
        pred.getSuccessors().remove(loop.header);
        pred.getSuccessors().add(preHeader);
        preHeader.getPredecessors().add(pred);

        loop.header.getPredecessors().remove(pred);
        loop.header.getPredecessors().add(preHeader);

        // PreHeader -> Header
        new JumpInstr(loop.header, preHeader);
        preHeader.getSuccessors().add(loop.header);

        // 更新 Header 中的 Phi 节点
        // 原本 Phi 接收 [val, pred]，现在应该接收 [val, preHeader]
        for (Instruction i : loop.header.getInstructions()) {
            if (i instanceof PhiInstr) {
                PhiInstr phi = (PhiInstr) i;
                int idx = phi.getIncomingBlocks().indexOf(pred);
                if (idx != -1) {
                    phi.getIncomingBlocks().set(idx, preHeader);
                }
            }
        }

        return preHeader;
    }

    private List<IrValue> getOperands(Instruction inst) {
        DeadCodeElimination dce = new DeadCodeElimination();
        return dce.getOperands(inst);
    }

    private static class Loop {
        IrBasicBlock header;
        Set<IrBasicBlock> body = new HashSet<>();
        List<IrBasicBlock> latches = new ArrayList<>();
    }
}