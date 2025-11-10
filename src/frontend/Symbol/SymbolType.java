package frontend.Symbol;

public enum SymbolType {
    CONST_INT("ConstInt"),
    CONST_INT_ARRAY("ConstIntArray"),
    STATIC_INT("StaticInt"),
    INT("Int"),
    INT_ARRAY("IntArray"),
    STATIC_INT_ARRAY("StaticIntArray"),
    VOID_FUNC("VoidFunc"),
    INT_FUNC("IntFunc"), ARRAY("Array"), NOT_ARRAY("NotArray"), NOT_EXIST("NotExist");
    private final String typeName;
    SymbolType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }
}