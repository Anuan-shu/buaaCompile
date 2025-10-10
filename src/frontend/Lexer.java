package frontend;

import java.io.FileInputStream;
import java.util.ArrayList;

public class Lexer {
    private final FileInputStream file;
    private final ArrayList<Token> tokens = new ArrayList<>();
    private int currentLine = 1;
    private char currentChar = ' ';

    public Lexer(FileInputStream file) {
        this.file = file;
        // 初始化
        // 读取第一个字符
        getChar();
    }
    public ArrayList<Token> getTokens() {
        return this.tokens;
    }
    public int getCurrentLine() {
        return this.currentLine;
    }

    public void analyse() {
        Token currentToken = null;
        while ((currentToken = getToken()) != null) {
            tokens.add(currentToken);
        }
    }

    private void getChar(){
        try {
            int ch = file.read();
            if (ch != -1) {
                currentChar = (char) ch;
            } else {
                currentChar = '\0';
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private Token getToken(){
        StringBuilder lexeme = new StringBuilder();
        skipWhitespace();
        //首字符判断
        if (isLetter(currentChar) || isUnderscore(currentChar)) {
            // 处理标识符或关键字
            return handleIdentifierOrKeyword(lexeme);
        } else if (isDigit(currentChar)) {
            // 处理整数常量
            return handleIntegerConstant(lexeme);
        }else if(isQuote(currentChar)){
            // 处理字符串常量
            return handleStringConstant(lexeme);
        }else if(isSlash(currentChar)){
            // 处理注释或除号
            return handleCommentOrDivide(lexeme);
        }else if(isPlus(currentChar)||isMinus(currentChar)||isAsterisk(currentChar)||isPercent(currentChar)||
                isSemi(currentChar)||isComma(currentChar)||isLParen(currentChar)||isRParen(currentChar)||isLBrack(currentChar)||
                isRBrack(currentChar)||isLBrace(currentChar)||isRBrace(currentChar)){
            // 处理单字符运算符或分隔符
            return handleSingle(lexeme);
        }
    }

    private Token handleSingle(StringBuilder lexeme) {
        lexeme.append(currentChar);
        getChar();
        return new Token(turn2tokenType(lexeme.toString()), lexeme.toString(), currentLine);
    }

    private Token handleCommentOrDivide(StringBuilder lexeme) {
        lexeme.append(currentChar);
        getChar();
        if (isSlash(currentChar)) {
            // 单行注释
            while (currentChar != '\n' && currentChar != '\0') {
                getChar();
            }
            return getToken(); // 继续获取下一个Token
        } else if (isAsterisk(currentChar)) {
            // 多行注释
            getChar();
            while (true) {
                if (currentChar == '*') {
                    getChar();
                    if (isSlash(currentChar)) {
                        getChar();
                        break; // 注释结束
                    }
                } else {
                    if (isNewline(currentChar)) {
                        currentLine++;
                    }
                    getChar();
                }
            }
            return getToken(); // 继续获取下一个Token
        } else {
            // 除号
            return new Token(Token.TokenType.DIV, lexeme.toString(), currentLine);
        }
    }

    private Token handleStringConstant(StringBuilder lexeme) {
        do {
            lexeme.append(currentChar);
            getChar();
        } while (currentChar != '"' && currentChar != '\0');
        // 结尾的引号
        if (currentChar == '"') {
            lexeme.append(currentChar);
            getChar();
        }
        return new Token(Token.TokenType.STRCON, lexeme.toString(), currentLine);
    }

    private Token handleIntegerConstant(StringBuilder lexeme) {
        while (isDigit(currentChar)) {
            lexeme.append(currentChar);
            getChar();
        }
        return new Token(Token.TokenType.INTCON, lexeme.toString(), currentLine);
    }

    private Token handleIdentifierOrKeyword(StringBuilder lexeme) {
        while (isLetter(currentChar) || isDigit(currentChar) || isUnderscore(currentChar)) {
            lexeme.append(currentChar);
            getChar();
        }
        String lexemeStr = lexeme.toString();

        return  new Token(turn2tokenType(lexemeStr), lexemeStr, currentLine);
    }

    private boolean isSpace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
    }
    private boolean isNewline(char ch) {
        return ch == '\n';
    }
    private boolean isTab(char ch) {
        return ch == '\t';
    }
    private boolean isLetter(char ch) {
        return Character.isLetter(ch);
    }
    private boolean isDigit(char ch) {
        return Character.isDigit(ch);
    }
    private boolean isUnderscore(char ch) {
        return ch == '_';
    }
    private boolean isSemi(char ch) {
        return ch == ';';
    }
    private boolean isComma(char ch) {
        return ch == ',';
    }
    private boolean isEqual(char ch) {
        return ch == '=';
    }
    private boolean isPlus(char ch) {
        return ch == '+';
    }
    private boolean isMinus(char ch) {
        return ch == '-';
    }
    private boolean isAsterisk(char ch) {
        return ch == '*';
    }
    private boolean isSlash(char ch) {
        return ch == '/';
    }
    private boolean isPercent(char ch) {
        return ch == '%';
    }
    private boolean isLParen(char ch) {
        return ch == '(';
    }
    private boolean isRParen(char ch) {
        return ch == ')';
    }
    private boolean isLBrack(char ch) {
        return ch == '[';
    }
    private boolean isRBrack(char ch) {
        return ch == ']';
    }
    private boolean isLBrace(char ch) {
        return ch == '{';
    }
    private boolean isRBrace(char ch) {
        return ch == '}';
    }
    private boolean isQuote(char ch) {
        return ch == '"';
    }

    private void skipWhitespace() {
        while (isSpace(currentChar)) {
            if (isNewline(currentChar)) {
                this.currentLine++;
            }
            getChar();
        }
    }
    public Token.TokenType turn2tokenType(String s) {
        return switch (s) {
            case "const" -> Token.TokenType.CONSTTK;
            case "int" -> Token.TokenType.INTTK;
            case "static" -> Token.TokenType.STATICTK;
            case "break" -> Token.TokenType.BREAKTK;
            case "continue" -> Token.TokenType.CONTINUETK;
            case "if" -> Token.TokenType.IFTK;
            case "main" -> Token.TokenType.MAINTK;
            case "else" -> Token.TokenType.ELSETK;
            case "!" -> Token.TokenType.NOT;
            case "&&" -> Token.TokenType.AND;
            case "||" -> Token.TokenType.OR;
            case "for" -> Token.TokenType.FORTK;
            case "return" -> Token.TokenType.RETURNTK;
            case "void" -> Token.TokenType.VOIDTK;
            case "+" -> Token.TokenType.PLUS;
            case "-" -> Token.TokenType.MINU;
            case "printf" -> Token.TokenType.PRINTFTK;
            case "*" -> Token.TokenType.MULT;
            case "/" -> Token.TokenType.DIV;
            case "%" -> Token.TokenType.MOD;
            case "<" -> Token.TokenType.LSS;
            case "<=" -> Token.TokenType.LEQ;
            case ">" -> Token.TokenType.GRE;
            case ">=" -> Token.TokenType.GEQ;
            case "==" -> Token.TokenType.EQL;
            case "!=" -> Token.TokenType.NEQ;
            case ";" -> Token.TokenType.SEMICN;
            case "," -> Token.TokenType.COMMA;
            case "(" -> Token.TokenType.LPARENT;
            case ")" -> Token.TokenType.RPARENT;
            case "[" -> Token.TokenType.LBRACK;
            case "]" -> Token.TokenType.RBRACK;
            case "{" -> Token.TokenType.LBRACE;
            case "}" -> Token.TokenType.RBRACE;
            case "=" -> Token.TokenType.ASSIGN;
            default -> Token.TokenType.IDENFR;
        };
    }
}
