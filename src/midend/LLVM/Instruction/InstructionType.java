package midend.LLVM.Instruction;

import midend.LLVM.ValueType;

public class InstructionType {
    public static final InstructionType ALLOCATE = new InstructionType("allocate");
    public static final InstructionType LOAD = new InstructionType("load");
    public static final InstructionType STORE = new InstructionType("store");
    public static final InstructionType GEP = new InstructionType("gep");
    public static final InstructionType CALL = new InstructionType("call");
    public static final InstructionType ALU = new InstructionType("alu");
    public static final InstructionType CMP = new InstructionType("cmp");
    public static final InstructionType ZEXT = new InstructionType("zext");
    public static final InstructionType RETURN = new InstructionType("return");
    public static final InstructionType TRUNC = new InstructionType("trunc");
    public static final InstructionType BRANCH = new InstructionType("branch");
    public static final InstructionType JUMP = new InstructionType("jump");
    public static final InstructionType PRINT = new InstructionType("print");
    private String typeName;

    public InstructionType(String typeName) {
        this.typeName = typeName;
    }
}
