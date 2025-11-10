package frontend.Visit.Func;

import frontend.Error;
import frontend.Parser.MainFuncDef.Block;

//不创建新作用域（在参数处理时创建）
public class VisitorFuncBlock {
    public static void VisitFuncBlock(Block block,boolean isNeedReturn,boolean inLastOutOfFunc) {
        //语句块 Block → '{' { BlockItem } '}'
        boolean hasRightReturn = false;
        for(int i=0;i<block.GetChildrenCount();i++){
            if(block.getChildren().get(i).getType().name().equals("BlockItem")){
                if(VisitorFuncBlockItem.VisitFuncBlockItem(block.GetChildAsBlockItem(i),isNeedReturn)){
                    hasRightReturn = true;
                }
            }
        }
        if(isNeedReturn&&(!hasRightReturn)&&inLastOutOfFunc){
            Error error = new Error(Error.ErrorType.g, block.GetLastLineNumber(),"g");
            error.printToError(error);
        }
    }
}
