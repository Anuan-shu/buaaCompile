package backend;

import midend.LLVM.IrBuilder;
import midend.LLVM.IrModule;

import java.io.FileOutputStream;

public class Backend {
    private static boolean optimize;

    public void generateMips(boolean optimize) {
        IrModule irModule = IrBuilder.getIrModule();
        MipsBuilder.generate(irModule, optimize);
        Backend.optimize = optimize;
    }

    public static boolean getOptimize() {
        return optimize;
    }

    public void writeMipsToFile(String file) {
        MipsModule mipsModule = MipsBuilder.getMipsModule();
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(mipsModule.toString().getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
