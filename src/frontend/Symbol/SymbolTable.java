package frontend.Symbol;

import java.util.ArrayList;
import java.util.Hashtable;

public class SymbolTable {
    private final int depth;
    private int index;
    
    private final ArrayList<Symbol> symbolList=new ArrayList<>();
    private final Hashtable<String, Symbol> symbolTable=new Hashtable<>();
    
    private final SymbolTable fatherTable;
    private final ArrayList<SymbolTable> sonTables=new ArrayList<>();

    public SymbolTable(int depth,SymbolTable fatherTable) {
        this.depth=depth;
        this.fatherTable=fatherTable;
    }

    public void AddSymbol(Symbol symbol) {
        //同一作用域内不允许重名
        if(this.symbolTable.containsKey(symbol.GetSymbolName())){
            System.err.println("Error: Symbol "+symbol.GetSymbolName()+" redefined in the same scope.");
        } else {
            this.symbolList.add(symbol);
            this.symbolTable.put(symbol.GetSymbolName(),symbol);
        }
    }

    public void AddSonTable(SymbolTable symbolTable) {
        this.sonTables.add(symbolTable);
    }

    public SymbolTable GetNextSonTable() {
        return this.sonTables.get(++index);
    }

}