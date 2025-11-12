package midend.Visit.Decl;

import frontend.Parser.Decl.VarDef;

public class VisitorVarDef {
    public static void VisitVarDef(VarDef vardef) {
        //VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal // b
        if(vardef==null){
            return;
        }
        if(vardef.hasInitVal()) {
            //访问初始化值
            VisitorInitVal.VisitInitVal(vardef.GetInitVal());
        }
    }
}
