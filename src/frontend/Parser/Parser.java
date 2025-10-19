package frontend.Parser;

import frontend.Error;
import frontend.Token;
import frontend.Parser.Tree.GrammarType;
import java.util.ArrayList;

public class Parser {
    private  ArrayList<Token> tokens;
    private ComUnit root;
    private ArrayList<Error> errors;
    public Parser(ArrayList<Token> tokens) {
        this.tokens = tokens;
        this.root = new ComUnit(GrammarType.CompUnit,0, tokens);
        this.errors = new ArrayList<>();
    }
    public void analyse() {
        root.parser();
    }
}
