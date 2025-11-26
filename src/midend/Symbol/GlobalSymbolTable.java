package midend.Symbol;

import frontend.Parser.Decl.ConstDef;
import frontend.Parser.Decl.VarDef;
import frontend.Parser.FuncDef.FuncDef;

import java.util.ArrayList;

public class GlobalSymbolTable {
    private static final SymbolTable globalSymbolTable = new SymbolTable(1, OutSymbolTable.getOutSymbolTable());
    private static SymbolTable localSymbolTable = globalSymbolTable;
    private static int scopeDepth = 1;

    /**
     * 作用域序号加一（只表示创建顺序并非深度）
     *
     * @return 当前作用域序号
     */
    public static int addScopeDepth() {
        scopeDepth += 1;
        return scopeDepth;
    }

    public static SymbolTable getGlobalSymbolTable() {
        return globalSymbolTable;
    }

    public static SymbolTable getLocalSymbolTable() {
        return localSymbolTable;
    }

    public static void setLocalSymbolTable(SymbolTable symbolTable) {
        localSymbolTable = symbolTable;
    }

    /**
     * 进入子符号表
     */
    public static void enterSonSymbolTable() {
        localSymbolTable = localSymbolTable.GetNextSonTable();
    }

    /**
     * 回到父级作用域
     */
    public static void GoToFatherSymbolTable() {
        localSymbolTable = localSymbolTable.getFatherTable();
    }

    public static boolean symbolIsArray(SymbolType symbolType) {
        return symbolType == SymbolType.INT_ARRAY
                || symbolType == SymbolType.STATIC_INT_ARRAY
                || symbolType == SymbolType.CONST_INT_ARRAY;
    }

    public static void addConstDef(ConstDef constDef) {
        String Ident = constDef.GetIdent();
        SymbolType symbolType = constDef.GetSymbolType();
        int line = constDef.GetLineNumber();
        Symbol symbol = new Symbol(Ident, symbolType, line);
        if (symbolIsArray(symbolType)) {
            symbol.setSize(constDef.GetArraySize());
        }
        symbol.setInitValues(constDef.GetInitValues());
        localSymbolTable.AddSymbol(symbol);
    }

    public static void addVarDef(VarDef varDef, boolean isStatic) {
        String Ident = varDef.GetIdent();
        SymbolType symbolType = varDef.GetSymbolType();
        if (isStatic) {
            if (symbolType == SymbolType.INT) {
                symbolType = SymbolType.STATIC_INT;
            } else if (symbolType == SymbolType.INT_ARRAY) {
                symbolType = SymbolType.STATIC_INT_ARRAY;
            }
        }
        int line = varDef.GetLineNumber();
        Symbol symbol = new Symbol(Ident, symbolType, line);
        if (symbolIsArray(symbolType)) {
            symbol.setSize(varDef.GetArraySize());
        }
        symbol.setInitValues(varDef.GetInitValues());
        localSymbolTable.AddSymbol(symbol);
    }

    public static void addFuncDef(FuncDef funcDef, ArrayList<Symbol> funcParamList) {
        String Ident = funcDef.GetIdent();
        SymbolType symbolType = funcDef.GetSymbolType();
        int line = funcDef.GetLineNumber();
        Symbol symbol = new Symbol(Ident, symbolType, line);
        symbol.setFuncParamList(funcParamList);
        localSymbolTable.AddSymbol(symbol);
    }

    public static Symbol searchSymbolByIdent(String ident, int useLine) {
        if (ident != null) {
            SymbolTable currentSymbolTable = localSymbolTable;
            while (currentSymbolTable != null) {
                Symbol currentSymbol = currentSymbolTable.getSymbolByIdent(ident);
                if (currentSymbol == null) {
                    currentSymbolTable = currentSymbolTable.getFatherTable();
                } else {
                    if (currentSymbol.GetLineNumber() > useLine) {
                        currentSymbolTable = currentSymbolTable.getFatherTable();
                        continue;
                    }
                    return currentSymbol;
                }
            }
        }
        return null;
    }

    public static boolean isGlobalSymbol(Symbol constSymbol) {
        return globalSymbolTable.containsSymbol(constSymbol);
    }
}
