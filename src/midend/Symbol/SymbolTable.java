package midend.Symbol;

import java.util.ArrayList;
import java.util.Hashtable;
import frontend.Error;

import static java.lang.Integer.max;

public class SymbolTable {
    private final int depth;
    private int index;
    
    private final ArrayList<Symbol> symbolList=new ArrayList<>();
    private final Hashtable<String, Symbol> symbolTable=new Hashtable<>();
    
    private final SymbolTable fatherTable;
    private final ArrayList<SymbolTable> sonTables=new ArrayList<>();
    private boolean isWrite=false;
    public void setWrite(boolean write) {
        isWrite = write;
    }
    public boolean getIsWrite() {
        return isWrite;
    }
    public SymbolTable(int depth,SymbolTable fatherTable) {
        this.depth=depth;
        this.fatherTable=fatherTable;
    }
    public int GetDepth() {
        return depth;
    }
    public ArrayList<Symbol> GetSymbolList() {
        return symbolList;
    }

    public void AddSymbol(Symbol symbol) {
        //同一作用域内不允许重名
        if(this.symbolTable.containsKey(symbol.GetSymbolName())) {
            Symbol oldSymbol = this.symbolTable.get(symbol.GetSymbolName());
            int line = max(oldSymbol.GetLineNumber(),symbol.GetLineNumber());
            Error error = new Error(Error.ErrorType.b, line,"b");
            error.printToError(error);
        } else {
            this.symbolList.add(symbol);
            this.symbolTable.put(symbol.GetSymbolName(),symbol);
        }
    }

    public void AddSonTable(SymbolTable symbolTable) {
        this.sonTables.add(symbolTable);
    }

    public boolean hasNextSonTable() {
        return index<sonTables.size();
    }
    public SymbolTable GetNextSonTable() {
        return this.sonTables.get(index++);
    }

    public SymbolTable getFatherTable() {
        return fatherTable;
    }

    public Symbol getSymbolByIdent(String ident){
        return this.symbolTable.get(ident);
    }
}