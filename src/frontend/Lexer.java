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

    public void analyse() {
    }
}
