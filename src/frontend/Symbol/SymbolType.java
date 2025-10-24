package frontend.Symbol;

public enum SymbolType {
    CONST_INT("ConstInt"),
    CONST_INT_ARRAY("ConstIntArray"),
    STATIC_INT("StaticInt"),
    INT("Int"),
    INT_ARRAY("IntArray"),
    STATIC_INT_ARRAY("StaticIntArray"),
    VOID_FUNC("VoidFunc"),
    INT_FUNC("IntFunc");
    private final String typeName;
    SymbolType(String typeName) {
        this.typeName = typeName;
    }
}