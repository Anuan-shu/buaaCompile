package frontend.Symbol;

import frontend.Parser.Decl.ConstDef;
import frontend.Parser.Decl.VarDef;

public class GlobalSymbolTable {
    private static final SymbolTable globalSymbolTable=new SymbolTable(1,null);
    private static SymbolTable localSymbolTable=globalSymbolTable;
    private void setLocalSymbolTable(SymbolTable symbolTable){
        localSymbolTable=symbolTable;
    }
    public static void addConstDef(ConstDef constDef) {
        String Ident = constDef.GetIdent();
        SymbolType symbolType = constDef.GetSymbolType();

        Symbol symbol=new Symbol(Ident,symbolType);
        globalSymbolTable.AddSymbol(symbol);
    }

    public static void addVarDef(VarDef varDef, boolean isStatic) {
        String Ident = varDef.GetIdent();
        SymbolType symbolType = varDef.GetSymbolType();
        if(isStatic){
            if(symbolType==SymbolType.INT){
                symbolType=SymbolType.STATIC_INT;
            } else if(symbolType==SymbolType.INT_ARRAY){
                symbolType=SymbolType.STATIC_INT_ARRAY;
            }
        }
        Symbol symbol=new Symbol(Ident,symbolType);
        globalSymbolTable.AddSymbol(symbol);
    }
}
