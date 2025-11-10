package frontend.Visit.Stmt;

import frontend.Error;

public class VisitorJump {
    public static void VisitJump(boolean isJump,int line) {
        if(!isJump){
            Error error = new Error(Error.ErrorType.m,line ,"m");
            error.printToError(error);
        }
    }
}
