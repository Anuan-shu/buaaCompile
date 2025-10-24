package frontend.Visit.Decl;

import frontend.Parser.Decl.*;
import frontend.Symbol.GlobalSymbolTable;

import java.util.ArrayList;

public class VisitorDecl {
    public static void VisitDecl(Decl decl) {
        if(decl.isConstDecl()){
            VisitConstDecl(decl.getConstDecl());
        } else {
            VisitVarDecl(decl.getVarDecl());
        }
    }

    private static void VisitVarDecl(VarDecl varDecl) {
        ArrayList<VarDef> varDefs = varDecl.GetVarDefs();
        boolean isStatic = varDecl.isStatic();
        for(VarDef vardef : varDefs) {
            GlobalSymbolTable.addVarDef(vardef,isStatic);
        }

    }

    private static void VisitConstDecl(ConstDecl constDecl) {
        ArrayList<ConstDef> constDefs = constDecl.GetConstDefs();
        for(ConstDef constDef : constDefs) {
            GlobalSymbolTable.addConstDef(constDef);
        }
    }
}
