package midend.LLVM.value;

import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;

import java.util.ArrayList;

public class IrFunction extends IrValue {
    private ArrayList<IrParameter> parameters;
    private ArrayList<IrBasicBlock> basicBlocks;

    public IrFunction(ValueType valueType, IrType irType, String irName) {
        super(valueType, irType, irName);
        parameters = new ArrayList<>();
        basicBlocks = new ArrayList<>();
    }

    public void addBasicBlock(IrBasicBlock basicBlock) {
        basicBlocks.add(basicBlock);
    }

    public IrType GetReturnType() {
        return this.irType;
    }

    public void addParameter(IrParameter irParameter) {
        parameters.add(irParameter);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        //函数声明
        sb.append("define dso_local " + this.irType.toString() + " " + this.irName);
        //参数列表
        sb.append("(");
        for (int i = 0; i < parameters.size() - 1; i++) {
            sb.append(parameters.get(i).toString());
            sb.append(", ");
        }
        if (parameters.size() > 0) {
            sb.append(parameters.get(parameters.size() - 1).toString());
        }
        sb.append(") {\n");
        //基本块
        for (int i = 0; i < basicBlocks.size() - 1; i++) {
            sb.append(basicBlocks.get(i).toString());
            sb.append("\n");
        }
        if (basicBlocks.size() > 0) {
            sb.append(basicBlocks.get(basicBlocks.size() - 1).toString());
        }
        sb.append("}\n");
        return sb.toString();
    }
}
