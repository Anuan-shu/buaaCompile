package midend.LLVM;

import midend.LLVM.Const.IrConstString;
import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrFunction;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IrModule extends IrValue {
    private final ArrayList<String> declares;
    private final ArrayList<IrFunction>functions;
    private final ArrayList<IrGlobalValue> globals;
    private final HashMap<String, IrConstString>stringIrConstStringHashMap;
    public IrModule(ValueType valueType, IrType irType, String irName) {
        super(valueType,irType, irName);

        this.declares = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.globals = new ArrayList<>();
        this.stringIrConstStringHashMap = new HashMap<>();
        this.declares.add("declare i32 @getint()");
        this.declares.add("declare void @putint(i32)");
        this.declares.add("declare void @putstr(i8*)");
    }

    public IrConstString GetNewIrConstString(String string) {
        if(this.stringIrConstStringHashMap.containsKey(string)) {
            return this.stringIrConstStringHashMap.get(string);
        }else{
            IrConstString irConstString = new IrConstString(string,IrBuilder.GetStringConstName());
            this.stringIrConstStringHashMap.put(string, irConstString);
            return irConstString;
        }
    }

    public ArrayList<String> getDeclares() {
        return declares;
    }
    public ArrayList<IrFunction> getFunctions() {
        return functions;
    }
    public ArrayList<IrGlobalValue> getGlobals() {
        return globals;
    }
    public HashMap<String, IrConstString> getStringIrConstStringHashMap() {
        return stringIrConstStringHashMap;
    }

    public void addFunction(IrFunction irFunction) {
        this.functions.add(irFunction);
    }

    public void addGlobalValue(IrGlobalValue irGlobalValue) {
        this.globals.add(irGlobalValue);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        //输出声明
        for (String declare : declares) {
            sb.append(declare).append("\n");
        }

        List<Map.Entry<String, IrConstString>> stringEntries = new ArrayList<>(this.stringIrConstStringHashMap.entrySet());
        //输出字符串常量
        for (Map.Entry<String, IrConstString> entry : stringEntries) {
            sb.append(entry.getValue()).append("\n");
        }
        //输出全局变量
        for (IrGlobalValue global : globals) {
            sb.append(global).append("\n");
        }
        //输出函数
        for (IrFunction function : functions) {
            sb.append(function).append("\n");
        }
        return sb.toString();
    }
}
