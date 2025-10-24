package frontend.Symbol;

public class Symbol {
    private final String symbolName;
    private final SymbolType symbolType;

    public Symbol(String symbolName, SymbolType symbolType) {
        this.symbolName = symbolName;
        this.symbolType = symbolType;
    }

    public String GetSymbolName() {
        return symbolName;
    }
    public SymbolType GetSymbolType() {
        return symbolType;
    }
}
