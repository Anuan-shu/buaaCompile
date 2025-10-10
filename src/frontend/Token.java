package frontend;

public class Token {
    public enum TokenType {
        IDENFR,
        INTCON, STRCON,
        CONSTTK, INTTK, STATICTK, BREAKTK, CONTINUETK, IFTK, MAINTK, ELSETK,
        NOT, AND, OR,
        FORTK, RETURNTK, VOIDTK,
        PLUS, MINU, PRINTFTK, MULT, DIV, MOD, LSS, LEQ, GRE, GEQ, EQL, NEQ,
        SEMICN, COMMA, LPARENT, RPARENT, LBRACK, RBRACK, LBRACE, RBRACE, ASSIGN,
    }

    private final TokenType type;
    private final String lexeme;
    private final int line;

    public Token(TokenType type, String lexeme, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        return this.lexeme;
    }
}
