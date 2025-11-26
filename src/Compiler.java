import frontend.Error;
import frontend.GlobalError;
import frontend.Lexer;
import frontend.Parser.Parser;
import midend.Symbol.GlobalSymbolTable;
import midend.Symbol.Symbol;
import midend.Symbol.SymbolTable;
import midend.Visit.Visitor;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;

public class Compiler {
    public static void main(String[] args) {
        String testfile = "testfile.txt";//输入文件
        String lexerfile = "parser.txt";//输出文件
        String errorfile = "error.txt";//错误文件

        try {
            FileInputStream fis = new FileInputStream(testfile);
            Lexer lexer = new Lexer(fis);
            lexer.analyse();
//          writeTokensToFile(lexerfile,lexer);
//          writeErrorsToFile(errorfile,lexer);
            Parser parser = new Parser(lexer.getTokens());
            parser.analyse();
            Visitor visitor = new Visitor(parser.getRoot());
            visitor.Visit();
//            writeSymbolTableToFile("symbol.txt");
//            writeAllErrorsToFile(errorfile);
            visitor.llvmVisit();
            visitor.writeLLVMToFile("llvm_ir.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeAllErrorsToFile(String errorfile) {
        try {
            FileWriter writer = new FileWriter(errorfile);
            ArrayList<Error> allErrors = GlobalError.getErrors();
            //按行号从小到大输出
            allErrors.sort(Comparator.comparingInt(Error::getLine));
            for (Error error : allErrors) {
                if(GlobalError.isPrinted(error.getLine())){
                    continue;
                }
                writer.write(error + "\n");
                GlobalError.setPrinted(error.getLine());
            }

            writer.close();
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

    public static void writeSymbolTableToFile(String symbolFile){
        try {
            FileWriter writer = new FileWriter(symbolFile);
            SymbolTable currentTable = GlobalSymbolTable.getGlobalSymbolTable();
            while (currentTable != null) {
                if(currentTable.getIsWrite()){
                    if(currentTable.hasNextSonTable()){
                        currentTable = currentTable.GetNextSonTable();
                    }else{
                        currentTable = currentTable.getFatherTable();
                    }
                }else{
                    int dep = currentTable.GetDepth();
                    if(dep==0){
                        break;
                    }
                    for (Symbol symbol : currentTable.GetSymbolList()) {
                        writer.write(dep+" "+symbol.GetSymbolName()+" "+symbol.GetSymbolType().getTypeName()+"\n");
                        //System.out.println(dep+" "+symbol.GetSymbolName()+" "+ symbol.GetSymbolType().getTypeName());
                    }
                    currentTable.setWrite(true);
                    if(currentTable.hasNextSonTable()){
                        currentTable = currentTable.GetNextSonTable();
                    }else{
                        currentTable = currentTable.getFatherTable();
                    }
                }
            }
            writer.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
