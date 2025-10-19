package frontend.Parser.Tree;

import frontend.Error;
import frontend.Token;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class  Node {
    private GrammarType type;
    private Node parent;
    private ArrayList<Node> children;
    private ArrayList<Token> tokens;
    private int tokenIndex;
    private Token token;
    private final String filename="parser.txt";
    private final String ErrorFilename="error.txt";
    public Node(GrammarType type,int tokenIndex, ArrayList<Token> tokens) {
        this.type = type;
        this.children = new ArrayList<>();
        this.parent = null;
        this.tokens = tokens;
        this.tokenIndex = tokenIndex;
    }
    public GrammarType getType() {
        return type;
    }
    public String getTypeName() {
        return type.name();
    }
    public Node getParent() {
        return parent;
    }
    public void setParent(Node parent) {
        this.parent = parent;
    }
    public ArrayList<Node> getChildren() {
        return children;
    }
    public void addChild(Node child) {
        child.setParent(this);
        this.children.add(child);
    }
    public ArrayList<Token> getTokens() {
        return tokens;
    }
    public void setTokens(ArrayList<Token> tokens) {
        this.tokens = tokens;
    }
    public Token getTokenAt(int index) {
        if (tokens == null || index < 0 || index >= tokens.size()) {
            return null;
        }
        return tokens.get(index);
    }

    protected Token peekToken(int offset) {
        if (tokens == null) {
            return null;
        }
        int index = tokenIndex + offset;
        if (index < 0 || index >= tokens.size()) {
            return null;
        }
        return tokens.get(index);
    }

    public int getIndex() {
        return tokenIndex;
    }
    public void setIndex(int index) {
        this.tokenIndex = index;
    }
    public void setToken(Token token) {
        this.token = token;
    }
    public void printToFile() {
        //输出到filename
        try (FileWriter writer = new FileWriter(filename, true)) {
            writer.write(token.getType()+" "+token.getLexeme() +"\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void printToError(Error error) {
        try {
            FileWriter writer = new FileWriter(ErrorFilename, true);
            writer.write(error.toString() + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void printTypeToFile(){
        //输出节点类型到filename
        try (FileWriter writer = new FileWriter(filename, true)) {
            writer.write("<"+this.getTypeName() +">"+"\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
