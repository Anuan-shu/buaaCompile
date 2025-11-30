package midend.SSA;

import midend.LLVM.Instruction.AllocateInstruction;
import midend.LLVM.Instruction.Instruction;
import midend.LLVM.Instruction.InstructionType;
import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrBasicBlock;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class PhiInstr extends Instruction {
    // 记录来源于哪个 Alloca (仅用于 Mem2Reg 阶段，后续可清空)
    private AllocateInstruction originalAlloca;

    // incoming values 和 incoming blocks 成对出现
    private ArrayList<IrValue> values = new ArrayList<>();
    private ArrayList<IrBasicBlock> blocks = new ArrayList<>();

    public PhiInstr(IrType type, IrBasicBlock parent) {
        super(ValueType.PHI_INST, type, IrBuilder.GetPhiName(), InstructionType.PHI);
    }

    public void setOriginalAlloca(AllocateInstruction alloca) {
        this.originalAlloca = alloca;
    }

    public AllocateInstruction getOriginalAlloca() {
        return originalAlloca;
    }

    /**
     * 添加分支来源
     *
     * @param value 来源的值
     * @param block 来源的基本块
     */
    public void addIncoming(IrValue value, IrBasicBlock block) {
        this.values.add(value);
        this.blocks.add(block);
        // 记得维护 Use 关系，这里简化省略
    }

    public ArrayList<IrValue> getIncomingValues() {
        return values;
    }

    public ArrayList<IrBasicBlock> getIncomingBlocks() {
        return blocks;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(irName).append(" = phi ").append(irType).append(" ");
        for (int i = 0; i < values.size(); i++) {
            sb.append("[ ").append(values.get(i).irName).append(", %").append(blocks.get(i).irName.substring(1)).append(" ]");
            if (i < values.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    @Override
    public void replaceUse(IrValue oldVal, IrValue newVal) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == oldVal) {
                values.set(i, newVal);
            }
        }
    }
}