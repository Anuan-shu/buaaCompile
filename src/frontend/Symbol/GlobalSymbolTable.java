package frontend.Symbol;

import frontend.Parser.Decl.ConstDef;
import frontend.Parser.Decl.VarDef;
import frontend.Parser.FuncDef.FuncDef;

import java.util.ArrayList;

public class GlobalSymbolTable {
    private static final SymbolTable globalSymbolTable=new SymbolTable(1,OutSymbolTable.getOutSymbolTable());
    private static SymbolTable localSymbolTable=globalSymbolTable;
    private static int scopeDepth=1;
    public static int addScopeDepth(){
        scopeDepth+=1;
        return scopeDepth;
    }
    public static SymbolTable getGlobalSymbolTable(){
        return globalSymbolTable;
    }
    public static SymbolTable getLocalSymbolTable(){
        return localSymbolTable;
    }
    public static void setLocalSymbolTable(SymbolTable symbolTable){
        localSymbolTable=symbolTable;
    }

    public static void addConstDef(ConstDef constDef) {
        String Ident = constDef.GetIdent();
        SymbolType symbolType = constDef.GetSymbolType();
        int line = constDef.GetLineNumber();
        Symbol symbol=new Symbol(Ident,symbolType,line);
        localSymbolTable.AddSymbol(symbol);
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
        int line = varDef.GetLineNumber();
        Symbol symbol=new Symbol(Ident,symbolType,line);
        localSymbolTable.AddSymbol(symbol);
    }

    public static void addFuncDef(FuncDef funcDef, ArrayList<Symbol> funcParamList) {
        String Ident =funcDef.GetIdent();
        SymbolType symbolType = funcDef.GetSymbolType();
        int line = funcDef.GetLineNumber();
        Symbol symbol=new Symbol(Ident,symbolType,line);
        symbol.setFuncParamList(funcParamList);
        localSymbolTable.AddSymbol(symbol);
    }

    public static Symbol searchSymbolByIdent(String ident) {
        if (ident != null) {
            SymbolTable currentSymbolTable = localSymbolTable;
            while (currentSymbolTable != null) {
                Symbol currentSymbol = currentSymbolTable.getSymbolByIdent(ident);
                if (currentSymbol == null) {
                    currentSymbolTable = currentSymbolTable.getFatherTable();
                } else {
                    return currentSymbol;
                }
            }
        }
        return null;
    }
}
