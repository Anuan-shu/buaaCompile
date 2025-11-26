package midend.LLVM.value;

import midend.LLVM.Instruction.*;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

import java.util.ArrayList;
import java.util.Map;

public class IrBasicBlock extends IrValue {
    private final ArrayList<Instruction> instructions;
    private final IrFunction function;

    public IrBasicBlock(ValueType valueType, IrType irType, String irName, IrFunction function) {
        super(valueType, irType, irName);
        this.instructions = new ArrayList<>();
        this.function = function;
    }

    public void addInstruction(Instruction instruction) {
        instructions.add(instruction);
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
}
