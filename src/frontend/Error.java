package frontend;

import java.util.HashMap;
import java.util.Map;

public class Error {
    public enum ErrorType {
        a,b,c,d,e,f,g,h,i,j,k,l,m
    }

    private final ErrorType type;
    private final int line;
    private final String message;

    public Error(ErrorType type, int line, String message) {
        this.type = type;
        this.line = line;
        this.message = message;
    }

    public ErrorType getType() {
        return type;
    }

    public int getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return line + " " + message;
    }
}
