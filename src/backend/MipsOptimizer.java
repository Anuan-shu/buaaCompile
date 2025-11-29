package backend;

import java.util.ArrayList;
import java.util.List;

public class MipsOptimizer {

    // 入口方法
    public static List<String> optimize(List<String> sourceCode) {
        List<String> optimized = new ArrayList<>(sourceCode);

        // 多次扫描，直到没有优化空间为止
        optimized = removeRedundantLoad(optimized);

        optimized = removeRedundantJumps(optimized);

        return optimized;
    }

    /**
     * 窥孔优化：消除冗余的 Load 指令
     * 模式：
     * sw $t0, offset($fp)
     * lw $t1, offset($fp)
     * 优化为：
     * sw $t0, offset($fp)
     * move $t1, $t0
     */
    private static List<String> removeRedundantLoad(List<String> lines) {
        List<String> result = new ArrayList<>();

        String lastOp = "";
        String lastReg = "";
        String lastAddr = "";

        for (String line : lines) {
            // 解析当前行
            String trimmed = line.trim();
            // 简单的分割：按空格或逗号
            // 示例: "sw $t0, -24($fp)" -> ["sw", "$t0", "-24($fp)"]
            String[] parts = trimmed.split("[\\s,]+");

            boolean keepLine = true;
            String currentOp = "";
            String currentReg = "";
            String currentAddr = "";

            if (parts.length == 3 && parts[2].endsWith("($fp)")) {
                currentOp = parts[0];
                currentReg = parts[1];
                currentAddr = parts[2];
            }

            // 检查是否匹配模式
            if (currentOp.equals("lw")) {
                // 如果上一条是 sw，且地址相同
                if (lastOp.equals("sw") && lastAddr.equals(currentAddr)) {
                    // 命中优化
                    if (lastReg.equals(currentReg)) {
                        // 情况A: sw $t0, ...; lw $t0, ...
                        // 直接删除 lw，因为寄存器值没变
                        keepLine = false;
                        // 添加注释方便调试
                        result.add("# [opt] removed redundant lw " + currentReg);
                    } else {
                        // 情况B: sw $t0, ...; lw $t1, ...
                        // 替换为 move $t1, $t0
                        String moveInst = "\tmove " + currentReg + ", " + lastReg;
                        result.add(moveInst + " # [opt] replaced lw with move");
                        keepLine = false;

                        // 更新状态：现在的 currentOp 相当于 move，破坏了 sw-lw 链，清空状态
                        // 因为 move 之后，$t1 的值虽然对了，但它不是从内存读的，
                        // 如果下一条还是 lw $t2, addr，我们不能简单地用 $t1 代替，
                        // 为了安全，这里断开。
                        lastOp = "move";
                    }
                }
            }

            // 遇到 Label (xx:) 或者 跳转指令 (b, j, jal)，必须清空状态
            // 跳转可能导致控制流改变，上一条指令不一定是 sw
            if (trimmed.endsWith(":") || trimmed.startsWith("b") || trimmed.startsWith("j")) {
                lastOp = "";
                lastReg = "";
                lastAddr = "";
            }
            // 如果这一行保留，且是 sw 指令，更新状态
            else if (keepLine) {
                if (currentOp.equals("sw")) {
                    lastOp = "sw";
                    lastReg = currentReg;
                    lastAddr = currentAddr;
                } else if (currentOp.equals("lw")) {
                    // 如果是 lw，也更新状态，防止误判
                    lastOp = "lw";
                } else {
                    // 其他指令（如 add, sub），打断 sw-lw 链
                    lastOp = "";
                }
            }

            if (keepLine) {
                result.add(line);
            }
        }
        return result;
    }

    /**
     * 优化：去除无用的跳转
     * 模式：
     * j Label
     * nop
     * Label:
     * 这种跳转是多余的，直接删掉 j 和 nop，让程序自然顺序执行下去。
     */
    private static List<String> removeRedundantJumps(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String currentLine = lines.get(i).trim();

            boolean isRedundant = false;

            // 检查是否是 j 指令
            if (currentLine.startsWith("j ")) {
                // 提取目标标签，格式 "j LabelName"
                // split 结果: ["j", "LabelName"]
                String[] parts = currentLine.split("\\s+");
                if (parts.length >= 2) {
                    String targetLabel = parts[1];

                    // 向后看 2 行：
                    // i+1 应该是 nop
                    // i+2 应该是 TargetLabel:
                    if (i + 2 < lines.size()) {
                        String nextLine = lines.get(i + 1).trim();
                        String nextNextLine = lines.get(i + 2).trim();

                        if (nextLine.equals("nop") && nextNextLine.equals(targetLabel + ":")) {
                            // 命中模式
                            isRedundant = true;
                        }
                    }
                }
            }

            if (isRedundant) {
                // 添加注释，方便调试确认优化生效
                result.add("# [opt] removed redundant jump: " + currentLine);
                // 跳过下一行 (nop)
                i++;
            } else {
                result.add(lines.get(i));
            }
        }
        return result;
    }
}