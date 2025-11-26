package midend.Symbol;

import midend.LLVM.Type.IrType;
import midend.LLVM.value.IrGlobalValue;
import midend.LLVM.value.IrValue;

import java.util.ArrayList;

public class Symbol {
    private final String symbolName;
    private final SymbolType symbolType;
    private final int lineNumber;
    private ArrayList<Symbol> params=new ArrayList<>();
    private int size=0;
    private ArrayList<Integer>initValues=new ArrayList<>();
    private IrValue IrValue;

    public Symbol(String symbolName, SymbolType symbolType, int lineNumber) {
        this.symbolName = symbolName;
        this.symbolType = symbolType;
        this.lineNumber = lineNumber;
    }
    public Symbol(String symbolName, SymbolType symbolType, int lineNumber,IrValue irValue) {
        this.symbolName = symbolName;
        this.symbolType = symbolType;
        this.lineNumber = lineNumber;
        this.IrValue=irValue;
    }

    public String GetSymbolName() {
        return symbolName;
    }
    public SymbolType GetSymbolType() {
        return symbolType;
    }

    public int GetLineNumber(){
        return lineNumber;
    }

    public void setFuncParamList(ArrayList<Symbol> funcParamList) {
        this.params = funcParamList;
    }

    public ArrayList<Symbol> getParamList() {
        return params;
    }

    public boolean isConstArray() {
        return this.symbolType.equals(SymbolType.CONST_INT_ARRAY);
    }
    public boolean isArray(){
        return this.symbolType.equals(SymbolType.ARRAY)
                || this.symbolType.equals(SymbolType.CONST_INT_ARRAY)
                || this.symbolType.equals(SymbolType.INT_ARRAY)
                || this.symbolType.equals(SymbolType.STATIC_INT_ARRAY);
    }

    public void setInitValues(ArrayList<Integer> initValues) {
        this.initValues = initValues;
    }
    public ArrayList<Integer> getInitValues() {
        return initValues;
    }

    public void setSize(int size) {
        this.size = size;
    }
    public int getSize() {
        return size;
    }

    public void setIrValue(IrValue irGlobalValue) {
        this.IrValue = irGlobalValue;
    }

    public IrType getIrType() {
        if(this.symbolType.equals(SymbolType.INT)){
            return IrType.INT32;
        }else if(this.symbolType.equals(SymbolType.CONST_INT)){
            return IrType.INT32;
        }else if(this.symbolType.equals(SymbolType.INT_ARRAY)){
            IrType irType=new IrType("array");
            irType.arraySize=this.size;
            return irType;
        }else if(this.symbolType.equals(SymbolType.CONST_INT_ARRAY)){
            IrType irType=new IrType("array");
            irType.arraySize=this.size;
            return irType;
        }else if(this.symbolType.equals(SymbolType.STATIC_INT)){
            return IrType.INT32;
        }else if(this.symbolType.equals(SymbolType.STATIC_INT_ARRAY)){
            IrType irType=new IrType("array");
            irType.arraySize=this.size;
            return irType;
        }
        else{
            throw new RuntimeException("Unknown symbol type for IrType retrieval");
        }
    }

    public IrValue getIrValue() {
        return IrValue;
    }
}
