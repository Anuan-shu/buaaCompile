package midend.Symbol;

public class OutSymbolTable {
    private static final SymbolTable symbolTable=new SymbolTable(0,null);

    public static SymbolTable getOutSymbolTable() {
        return symbolTable;
    }
    public static void addSymbol(Symbol symbol){
        symbolTable.AddSymbol(symbol);
    }
}
