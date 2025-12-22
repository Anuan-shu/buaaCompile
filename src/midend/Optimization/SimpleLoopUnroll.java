package midend.Optimization;

import midend.LLVM.Const.IrConstInt;
import midend.LLVM.Const.IrConstString;
import midend.LLVM.Instruction.*;
import midend.LLVM.IrModule;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;
import midend.SSA.PhiInstr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SimpleLoopUnroll {

    // 阈值设置 - 更激进的优化以减少跳转
    private static final int MAX_TRIP_COUNT = 1024;
    private static final int MAX_INSTRUCTIONS_THRESHOLD = 8000;

    public void run(IrModule module) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrFunction func : module.getFunctions()) {
                // 快照遍历，防止并发修改异常
                List<IrBasicBlock> blocks = new ArrayList<>(func.getBasicBlocks());
                for (IrBasicBlock bb : blocks) {
                    if (func.getBasicBlocks().contains(bb)) {
                        // 尝试分析并展开
                        if (processBlock(func, bb)) {
                            changed = true;
                            break; // CFG 改变，重新扫描函数
                        }
                    }
                }
                if (changed) break;
            }
        }
    }

    private boolean processBlock(IrFunction func, IrBasicBlock header) {
        // 1. 分析是否为可展开的循环
        LoopInfo info = analyzeLoop(header);

        // 2. 如果分析成功且符合展开条件，执行展开
        if (info != null && info.canUnroll) {
            unrollLoop(func, info);
            return true;
        }
        return false;
    }

    // --- 分析部分 ---
    private static class LoopInfo {
        IrBasicBlock header;
        IrBasicBlock latch; // 即 body
        IrBasicBlock exit;
        IrBasicBlock body;  // 在简单循环中 body == latch

        PhiInstr indVar;    // 归纳变量 i
        List<PhiInstr> allPhis = new ArrayList<>(); // 所有 phi 节点
        int startVal;
        int endVal;
        int stepVal;
        long tripCount;
        boolean canUnroll = false;
    }

    private LoopInfo analyzeLoop(IrBasicBlock header) {
        Instruction term = header.getTerminator();
        if (!(term instanceof BranchInstr)) return null;
        BranchInstr br = (BranchInstr) term;
        if (br.getCond() == null) return null;

        IrBasicBlock body = br.getTrueBlock();
        IrBasicBlock exit = br.getFalseBlock();

        Instruction bodyTerm = body.getTerminator();
        boolean bodyJumpsBack = false;
        if (bodyTerm instanceof BranchInstr) {
            BranchInstr bodyBr = (BranchInstr) bodyTerm;
            if (bodyBr.getCond() == null && bodyBr.getTrueBlock() == header) bodyJumpsBack = true;
        } else if (bodyTerm instanceof JumpInstr) {
            if (((JumpInstr) bodyTerm).getTargetBlock() == header) bodyJumpsBack = true;
        }
        if (!bodyJumpsBack) {
            // System.err.println("[Unroll] " + header.irName + ": body doesn't jump back");
            return null;
        }

        List<PhiInstr> phis = new ArrayList<>();
        for (Instruction i : header.getInstructions()) {
            if (i instanceof PhiInstr) phis.add((PhiInstr) i);
        }
        if (phis.isEmpty())
            return null;

        // 找到用于循环条件的归纳变量 phi
        if (!(br.getCond() instanceof CmpInstr)) return null;
        CmpInstr cmp = (CmpInstr) br.getCond();
        if (!(cmp.getRight() instanceof IrConstInt))
            return null;

        // 查找 cmp.getLeft() 对应的 phi
        PhiInstr phi = null;
        for (PhiInstr p : phis) {
            if (cmp.getLeft() == p) {
                phi = p;
                break;
            }
        }
        if (phi == null)
            return null;

        int bound = ((IrConstInt) cmp.getRight()).getValue();
        int start = 0;
        int step = 0;

        if (phi.getIncomingBlocks().size() != 2) return null;
        int bodyIdx = (phi.getIncomingBlocks().get(0) == body) ? 0 : 1;
        IrValue startV = phi.getIncomingValues().get(1 - bodyIdx);
        if (!(startV instanceof IrConstInt)) return null;
        start = ((IrConstInt) startV).getValue();

        IrValue stepV = phi.getIncomingValues().get(bodyIdx);
        if (!(stepV instanceof AluInst)) return null;
        AluInst stepInst = (AluInst) stepV;
        if (!stepInst.getOp().equals("ADD")) return null;

        if (stepInst.getLeft() == phi && stepInst.getRight() instanceof IrConstInt) {
            step = ((IrConstInt) stepInst.getRight()).getValue();
        } else if (stepInst.getRight() == phi && stepInst.getLeft() instanceof IrConstInt) {
            step = ((IrConstInt) stepInst.getLeft()).getValue();
        } else {
            return null;
        }

        if (step <= 0) return null;
        String pred = cmp.getOp();
        long count = 0;

        if (pred.equals("SLT") || pred.equals("LT")) {
            if (start >= bound) return null;
            count = (bound - start + step - 1) / step;
        } else if (pred.equals("SLE") || pred.equals("LE")) {
            if (start > bound) return null;
            count = (bound - start) / step + 1;
        } else {
            return null;
        }

        if (count > MAX_TRIP_COUNT || count <= 0) return null;
        if (body.getInstructions().size() * count > MAX_INSTRUCTIONS_THRESHOLD) return null;

        LoopInfo info = new LoopInfo();
        info.header = header;
        info.body = body;
        info.latch = body;
        info.exit = exit;
        info.indVar = phi;
        info.allPhis = phis; // 保存所有 phi 节点
        info.startVal = start;
        info.endVal = bound;
        info.stepVal = step;
        info.tripCount = count;
        info.canUnroll = true;
        return info;
    }

    // --- 变换部分 ---

    private void unrollLoop(IrFunction func, LoopInfo info) {
        ArrayList<IrBasicBlock> newBlocks = new ArrayList<>();

        // 获取 PreHeader
        IrBasicBlock preHeader = null;
        for (IrBasicBlock pred : info.header.getPredecessors()) {
            if (pred != info.latch) {
                preHeader = pred;
                break;
            }
        }
        if (preHeader == null) return;

        // 1. 处理循环变量在循环外被使用的情况
        int finalValInt = info.startVal + (int) info.tripCount * info.stepVal;
        IrConstInt finalConst = new IrConstInt(finalValInt);

        // 遍历函数寻找循环外部的使用者并替换
        for (IrBasicBlock bb : func.getBasicBlocks()) {
            for (Instruction userInst : bb.getInstructions()) {
                if (userInst.getParent() != info.header && userInst.getParent() != info.body) {
                    // 调用 Instruction 类的 replaceUse
                    userInst.replaceUse(info.indVar, finalConst);
                }
            }
        }

        // 2. 清空 Header 原有指令 (Phi, Cmp, Br)
        info.header.getInstructions().clear();

        // 3. 开始展开
        IrBasicBlock currentBlock = preHeader;
        int currentI = info.startVal;

        for (int iter = 0; iter < info.tripCount; iter++) {
            // 创建新块
            IrBasicBlock clonedBlock = new IrBasicBlock(ValueType.BASIC_BLOCK, IrType.BASICBLOCK,
                    "unroll_" + iter, func);
            newBlocks.add(clonedBlock);

            // 建立值映射 (i -> const)
            HashMap<IrValue, IrValue> valueMap = new HashMap<>();
            IrConstInt constI = new IrConstInt(currentI);
            valueMap.put(info.indVar, constI);

            // 处理所有 phi 节点的初始值
            for (PhiInstr p : info.allPhis) {
                if (p == info.indVar)
                    continue; // 归纳变量已处理

                // 获取 phi 的前驱块索引
                int bodyIdx = -1;
                for (int j = 0; j < p.getIncomingBlocks().size(); j++) {
                    if (p.getIncomingBlocks().get(j) == info.body) {
                        bodyIdx = j;
                        break;
                    }
                }

                if (iter == 0) {
                    // 第一次迭代使用来自 preheader 的值
                    int preIdx = (bodyIdx == 0) ? 1 : 0;
                    IrValue initVal = p.getIncomingValues().get(preIdx);
                    valueMap.put(p, initVal);
                }
                // 后续迭代的值将由上一轮 valueMap 传递
            }

            // 复制 Body 指令
            for (Instruction inst : info.body.getInstructions()) {
                // 跳过跳转指令
                if (inst instanceof BranchInstr || inst instanceof JumpInstr) continue;
                if (inst instanceof PhiInstr) continue;

                Instruction newInst = copyInstruction(inst, valueMap);
                if (newInst != null) {
                    clonedBlock.addInstruction(newInst);
                    valueMap.put(inst, newInst);
                }
            }

            // 更新非归纳变量 phi 的下一次迭代值
            for (PhiInstr p : info.allPhis) {
                if (p == info.indVar)
                    continue;

                // 获取来自 body 的值
                for (int j = 0; j < p.getIncomingBlocks().size(); j++) {
                    if (p.getIncomingBlocks().get(j) == info.body) {
                        IrValue bodyVal = p.getIncomingValues().get(j);
                        if (valueMap.containsKey(bodyVal)) {
                            valueMap.put(p, valueMap.get(bodyVal));
                        }
                        break;
                    }
                }
            }

            // 链接上一块 -> 当前块
            if (currentBlock == preHeader) {
                // 修改 PreHeader 的跳转目标
                Instruction lastTerm = preHeader.getTerminator();
                if (lastTerm instanceof BranchInstr) {
                    BranchInstr br = (BranchInstr) lastTerm;
                    if (br.getTrueBlock() == info.header) br.setTrueBlock(clonedBlock);
                    else if (br.getFalseBlock() == info.header) br.setFalseBlock(clonedBlock);
                } else if (lastTerm instanceof JumpInstr) {
                    ((JumpInstr) lastTerm).setTargetBlock(clonedBlock);
                }
            } else {
                new JumpInstr(clonedBlock, currentBlock);
            }

            currentBlock = clonedBlock;
            currentI += info.stepVal;
        }

        // 4. 链接最后一块 -> Exit
        new JumpInstr(info.exit, currentBlock);

        // 5. 移除旧块
        func.getBasicBlocks().remove(info.header);
        func.getBasicBlocks().remove(info.body);

        // 6. 将新块添加到函数 Block 列表中
        // 找到 Exit 块的位置，插入到它前面
        int exitIndex = func.getBasicBlocks().indexOf(info.exit);
        if (exitIndex != -1) {
            func.getBasicBlocks().addAll(exitIndex, newBlocks);
        } else {
            func.getBasicBlocks().addAll(newBlocks);
        }
    }

    private Instruction copyInstruction(Instruction inst, HashMap<IrValue, IrValue> map) {
        Instruction newInst = null;

        if (inst instanceof AluInst) {
            AluInst i = (AluInst) inst;
            newInst = new AluInst(i.getOp(),
                    map.getOrDefault(i.getLeft(), i.getLeft()),
                    map.getOrDefault(i.getRight(), i.getRight()));
        } else if (inst instanceof LoadInstr) {
            LoadInstr i = (LoadInstr) inst;
            newInst = new LoadInstr(map.getOrDefault(i.getPtr(), i.getPtr()));
        } else if (inst instanceof StoreInstr) {
            StoreInstr i = (StoreInstr) inst;
            newInst = new StoreInstr(map.getOrDefault(i.getVal(), i.getVal()),
                    map.getOrDefault(i.getPtr(), i.getPtr()));
        } else if (inst instanceof GepInstr) {
            GepInstr i = (GepInstr) inst;
            newInst = new GepInstr(map.getOrDefault(i.getPtr(), i.getPtr()),
                    map.getOrDefault(i.getIndice(), i.getIndice()));
        } else if (inst instanceof CallInstr) {
            CallInstr i = (CallInstr) inst;
            ArrayList<IrValue> newArgs = new ArrayList<>();
            for (IrValue arg : i.getParameters()) newArgs.add(map.getOrDefault(arg, arg));
            newInst = new CallInstr(i.getTargetFunction(), newArgs);
        } else if (inst instanceof ZextInstr) {
            ZextInstr i = (ZextInstr) inst;
            newInst = new ZextInstr(map.getOrDefault(i.getOperand(0), i.getOperand(0)),
                    i.getTargetType());
        } else if (inst instanceof TruncInstr) {
            TruncInstr i = (TruncInstr) inst;
            newInst = new TruncInstr(map.getOrDefault(i.getOperand(0), i.getOperand(0)),
                    i.getTargetType());
        } else if (inst instanceof PrintIntInstr) {
            PrintIntInstr i = (PrintIntInstr) inst;
            newInst = new PrintIntInstr(map.getOrDefault(i.getPrintValue(), i.getPrintValue()));
        } else if (inst instanceof PrintStrInstr) {
            PrintStrInstr i = (PrintStrInstr) inst;
            newInst = new PrintStrInstr((IrConstString) i.getPrintValue());
        } else if (inst instanceof CmpInstr) {
            CmpInstr i = (CmpInstr) inst;
            newInst = new CmpInstr(i.getOp(),
                    map.getOrDefault(i.getLeft(), i.getLeft()),
                    map.getOrDefault(i.getRight(), i.getRight()));
        } else if (inst instanceof AllocateInstruction) {
            AllocateInstruction i = (AllocateInstruction) inst;
            newInst = new AllocateInstruction(i.irName, i.getAllocatedType());
        }

        return newInst;
    }
}