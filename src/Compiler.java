import frontend.Lexer;

import java.io.FileInputStream;
public class Compiler {
    public static void main(String[] args) {
        String testfile = "testfile.txt";//输入文件
        String lexerfile = "lexer.txt";//输出文件
        String errorfile = "error.txt";//错误文件

        try {
            FileInputStream fis = new FileInputStream(testfile);
            Lexer lexer = new Lexer(fis);
            lexer.analyse();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
