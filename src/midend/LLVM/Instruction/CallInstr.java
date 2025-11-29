package midend.LLVM.Instruction;

import midend.LLVM.IrBuilder;
import midend.LLVM.Type.IrType;
import midend.LLVM.ValueType;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class CallInstr extends Instruction {

    public CallInstr(IrFunction targetFunction, ArrayList<IrValue> paramList) {
        super(ValueType.CALL_INST, targetFunction.GetReturnType(), calName(targetFunction), InstructionType.CALL);
        this.AddUseValue(targetFunction);
        for (IrValue param : paramList) {
            this.AddUseValue(param);
        }
    }

    private static String calName(IrFunction targetFunction) {
        IrType returnType = targetFunction.GetReturnType();
        if (returnType.equals(IrType.VOID)) {
            return "void";
        } else {
            return IrBuilder.GetLocalVarName();
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        IrFunction targetFunction = (IrFunction) this.getUseValues().get(0);
        ArrayList<String> paramStrList = new ArrayList<>();
        for (int i = 1; i < this.getUseValues().size(); i++) {
            IrValue param = this.getUseValues().get(i);
            paramStrList.add(param.irType.toString() + " " + param.irName);
        }

        if (!targetFunction.GetReturnType().equals(IrType.VOID)) {
            sb.append(this.irName + " = ");
        }
        sb.append("call " + targetFunction.GetReturnType().toString() + " " + targetFunction.irName + "(");
        for (int i = 0; i < paramStrList.size() - 1; i++) {
            sb.append(paramStrList.get(i));
            sb.append(", ");
        }
        if (paramStrList.size() > 0) {
            sb.append(paramStrList.get(paramStrList.size() - 1));
        }
        sb.append(")");
        return sb.toString();
    }

    public ArrayList<IrValue> getParameters() {
        ArrayList<IrValue> params = new ArrayList<>();
        for (int i = 1; i < this.getUseValues().size(); i++) {
            params.add(this.getUseValues().get(i));
        }
        return params;
    }

    public IrFunction getTargetFunction() {
        return (IrFunction) this.getUseValues().get(0);
    }
}
