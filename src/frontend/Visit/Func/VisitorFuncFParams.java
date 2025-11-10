package frontend.Visit.Func;

import frontend.Parser.FuncDef.FuncFParams;
import frontend.Symbol.Symbol;

import java.util.ArrayList;

public class VisitorFuncFParams {
    public static ArrayList<Symbol> VisitFuncFParams(FuncFParams funcFParams) {
        //函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
        ArrayList<Symbol> params = new ArrayList<>();
        for (int i = 0; i < funcFParams.GetChildCount(); i++) {
            if (funcFParams.getChildren().get(i).getType().name().equals("FuncFParam")) {
                params.add(VisitorFuncFParam.VisitFuncFParam(funcFParams.GetChildAsFuncFParam(i)));
            }
        }
        return params;
    }
}
