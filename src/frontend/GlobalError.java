package frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GlobalError {
    private static ArrayList<Error> errors = new ArrayList<>();
    private static Map<Integer,Boolean> isPrintedMap= new HashMap<>();
    public static void addError(Error error) {
        errors.add(error);
    }
    public static ArrayList<Error> getErrors() {
        return errors;
    }
    public static boolean isPrinted(int line) {
        return isPrintedMap.getOrDefault(line,false);
    }
    public static void setPrinted(int line) {
        isPrintedMap.put(line,true);
    }
}
