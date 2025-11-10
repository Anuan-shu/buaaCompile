package frontend.Symbol;

import java.util.ArrayList;

public class Symbol {
    private final String symbolName;
    private final SymbolType symbolType;
    private final int lineNumber;
    private ArrayList<Symbol> params=new ArrayList<>();

    public Symbol(String symbolName, SymbolType symbolType, int lineNumber) {
        this.symbolName = symbolName;
        this.symbolType = symbolType;
        this.lineNumber = lineNumber;
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
}
