package backend;

import midend.LLVM.IrBuilder;
import midend.LLVM.IrModule;

import java.io.FileOutputStream;

public class Backend {

    public void generateMips() {
        IrModule irModule = IrBuilder.getIrModule();
        MipsBuilder.generate(irModule);
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
