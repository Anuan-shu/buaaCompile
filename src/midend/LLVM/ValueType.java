package midend.LLVM;

public enum ValueType {
    MOUDLE,          // 模块
    // Value
    ARGUMENT,        // 参数
    BASIC_BLOCK,     // 基本块

    // Value -> Constant
    CONSTANT,        // 常量标识符
    CONSTANT_DATA,   // 字面量
    CONST_INT,     // 整型常量
    CONST_INT_ARRAY, // 整型数组常量

    // Value -> Constant -> GlobalValue
    FUNCTION,
    GLOBAL_VARIABLE,

    // Value -> User -> Instruction
    BINARY_OPERATOR,
    COMPARE_INST,
    BRANCH_INST,
    RETURN_INST,
    STORE_INST,
    CALL_INST,
    INPUT_INST,
    OUTPUT_INST,
    ALLOCA_INST,
    GEP_INST,
    LOAD_INST,
    ALU_INST,
    UNARY_OPERATOR, ZEXT_INST, PARAMETER, TRUNC_INST, JUMP_INST, PRINT_INST,
}
