package midend.LLVM.value;

import midend.LLVM.Instruction.*;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IrBasicBlock extends IrValue {
    private final ArrayList<Instruction> instructions;
    private final IrFunction function;

    // 控制流图中的前驱和后继块
    private List<IrBasicBlock> predecessors = new ArrayList<>();
    private List<IrBasicBlock> successors = new ArrayList<>();

    // 获取指令列表的最后一条（终结指令）
    public Instruction getTerminator() {
        if (instructions.isEmpty()) return null;
        return instructions.get(instructions.size() - 1);
    }

    public List<IrBasicBlock> getPredecessors() {
        return predecessors;
    }

    public List<IrBasicBlock> getSuccessors() {
        return successors;
    }

    public void addSuccessor(IrBasicBlock bb) {
        if (!successors.contains(bb)) {
            successors.add(bb);
        }
    }

    public void addPredecessor(IrBasicBlock bb) {
        if (!predecessors.contains(bb)) {
            predecessors.add(bb);
        }
    }

    // 清空关系 (防止多次构建时重复添加)
    public void cleanSuccessors() {
        successors.clear();
        predecessors.clear();
    }

    public IrBasicBlock(ValueType valueType, IrType irType, String irName, IrFunction function) {
        super(valueType, irType, irName);
        this.instructions = new ArrayList<>();
        this.function = function;
    }

    public void addInstruction(Instruction instruction) {
        instructions.add(instruction);
        instruction.setParentBasicBlock(this);
    }

    public boolean isLastInstrReturn() {
        if (instructions.isEmpty()) {
            return false;
        }
        Instruction lastInstr = instructions.get(instructions.size() - 1);
        return lastInstr.getInstrType() == InstructionType.RETURN;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.irName).append(":\n");
        for (Instruction instruction : instructions) {
            sb.append("  ").append(instruction.toString()).append("\n");
        }
        return sb.toString();
    }

    public Instruction getLastInstruction() {
        if (instructions.isEmpty()) {
            return null;
        }
        return instructions.get(instructions.size() - 1);
    }

    public ArrayList<Instruction> getInstructions() {
        return instructions;
    }

    public boolean hasTerminator() {
        if (instructions.isEmpty()) {
            return false;
        }
        Instruction lastInstr = instructions.get(instructions.size() - 1);

        // 使用 instanceof 检查指令类型
        // 只要最后一条是跳转、条件分支或返回，说明块已经结束
        return lastInstr instanceof JumpInstr || lastInstr instanceof BranchInstr || lastInstr instanceof ReturnInstr;
    }

    public IrFunction getParent() {
        return function;
    }

    // 在列表头部插入指令 (Phi 节点必须在 Block 最前面)
    public void addInstructionFirst(Instruction instr) {
        this.instructions.add(0, instr);
        instr.setParentBasicBlock(this);
    }

    /**
     * 移除某个前驱块，并更新本块中 Phi 指令的 incoming 列表
     */
    public void removePredecessor(IrBasicBlock pred) {
        // 1. 移除 predecessor 列表中的引用
        this.predecessors.remove(pred);

        // 2. 遍历本块指令，更新所有 Phi 指令
        Iterator<Instruction> it = instructions.iterator();
        while (it.hasNext()) {
            Instruction inst = it.next();
            if (inst instanceof midend.SSA.PhiInstr) {
                midend.SSA.PhiInstr phi = (midend.SSA.PhiInstr) inst;

                // 查找并移除对应的 block 和 value
                ArrayList<IrBasicBlock> blocks = phi.getIncomingBlocks();
                ArrayList<midend.LLVM.value.IrValue> values = phi.getIncomingValues();

                for (int i = 0; i < blocks.size(); i++) {
                    if (blocks.get(i) == pred) {
                        blocks.remove(i);
                        values.remove(i);
                        i--; // 索引回退
                    }
                }
            } else {
                // Phi 指令一定在块的开头，遇到非 Phi 直接结束
                break;
            }
        }
    }
}
