package backend;

import java.util.ArrayList;
import java.util.List;

public class MipsModule {
    // 存放 .data 段的内容 (全局变量)
    private List<String> dataSection = new ArrayList<>();
    // 存放 .text 段的内容 (指令)
    private List<String> textSection = new ArrayList<>();

    public void addGlobal(String asm) {
        dataSection.add(asm);
    }

    public void addInst(String asm) {
        textSection.add(asm);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(".data\n");
        for (String s : dataSection) sb.append(s).append("\n");
        sb.append("\n.text\n");
        if (Backend.getOptimize()) {
            List<String> optimizedText = MipsOptimizer.optimize(this.textSection);
            for (String s : optimizedText) sb.append(s).append("\n");
        } else {
            for (String s : textSection) sb.append(s).append("\n");
        }
        return sb.toString();
    }
}