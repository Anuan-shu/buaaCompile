import frontend.Lexer;

import java.io.FileInputStream;
import java.io.FileWriter;

public class Compiler {
    public static void main(String[] args) {
        String testfile = "testfile.txt";//输入文件
        String lexerfile = "lexer.txt";//输出文件
        String errorfile = "error.txt";//错误文件

        try {
            FileInputStream fis = new FileInputStream(testfile);
            Lexer lexer = new Lexer(fis);
            lexer.analyse();

            writeTokensToFile(lexerfile,lexer);

            writeErrorsToFile(errorfile,lexer);

        } catch (Exception e) {
            e.printStackTrace();
        }



    }

    private static void writeErrorsToFile(String errorfile, Lexer lexer) {
        try {
            FileWriter writer = new FileWriter(errorfile);
            for (frontend.Error error : lexer.getErrors()) {
                writer.write(error.toString() + "\n");
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void writeTokensToFile(String lexerfile,Lexer lexer){
        try {
            FileWriter writer = new FileWriter(lexerfile);
            for (frontend.Token token : lexer.getTokens()) {
                writer.write(token.getType()+" "+ token.getLexeme() + "\n");
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
