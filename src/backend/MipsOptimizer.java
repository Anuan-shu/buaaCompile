package backend;

import java.util.ArrayList;
import java.util.List;

public class MipsOptimizer {

    // 入口方法
    public static List<String> optimize(List<String> sourceCode) {
        List<String> optimized = new ArrayList<>(sourceCode);
        boolean changed = true;
        int maxPasses = 100;
        int pass = 0;
        while (changed && pass < maxPasses) {
            int oldSize = optimized.size();
            List<String> current = new ArrayList<>(optimized);

            // 1. 先进行算术和Move优化，减少干扰
            current = removeRedundantMoves(current);
            current = simplifyAlgebra(current);

            // 2. 内存优化
            for (int i = 0; i < 4; i++) {
                current = removeRedundantLoad(current);
            }

            // 3. 分支优化
            current = optimizeBranches(current);
            current = removeRedundantJumps(current);

            optimized = current;
            changed = optimized.size() != oldSize;
            pass++;
        }

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
            // "sw $t0, -24($fp)" -> ["sw", "$t0", "-24($fp)"]
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
                        // 如果下一条还是 lw $t2, addr，不能简单地用 $t1 代替，
                        // 为了安全，这里断开。
                        lastOp = "move";
                    }
                } else if (lastOp.equals("lw") && lastAddr.equals(currentAddr)) {
                    if (lastReg.equals(currentReg)) {
                        // lw $t0, addr; lw $t0, addr -> 删除第二个
                        keepLine = false;
                        result.add("# [opt] removed duplicate lw " + currentReg);
                    } else {
                        // lw $t0, addr; lw $t1, addr -> move $t1, $t0
                        String moveInst = "\tmove " + currentReg + ", " + lastReg;
                        result.add(moveInst + " # [opt] replaced lw with move");
                        keepLine = false;
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

    /**
     * 优化：删除源和目的相同的 move 指令
     * 例如: move $t0, $t0 -> 删除
     */
    private static List<String> removeRedundantMoves(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("move")) {
                // move $t0, $t0
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length == 3 && parts[1].equals(parts[2])) {
                    result.add("# [opt] removed redundant move: " + line);
                    continue; // 跳过添加
                }
            }
            result.add(line);
        }
        return result;
    }

    /**
     * 优化：反转分支条件以消除紧随其后的无条件跳转
     */
    private static List<String> optimizeBranches(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.startsWith("beq") || trimmed.startsWith("bne") ||
                    trimmed.startsWith("beqz") || trimmed.startsWith("bnez")) {

                // 向后看: i (branch), i+1 (nop), i+2 (j), i+3 (nop), i+4 (Label:)
                // 检查 i+4 是否是 Branch 的目标
                if (i + 4 < lines.size()) {
                    String jumpLine = lines.get(i + 2).trim();
                    String labelLine = lines.get(i + 4).trim();

                    if (jumpLine.startsWith("j ") && labelLine.endsWith(":")) {
                        // 提取 Branch 的目标 Label
                        String[] branchParts = trimmed.split("[\\s,]+");
                        String branchTarget = branchParts[branchParts.length - 1]; // 最后一个参数是 label

                        // 提取 LabelLine 的 Label
                        String currentLabel = labelLine.substring(0, labelLine.length() - 1);

                        // 如果 Branch 的目标就是紧接着的 Label (Label_True)
                        if (branchTarget.equals(currentLabel)) {
                            // 提取 Jump 的目标
                            String jumpTarget = jumpLine.split("\\s+")[1];

                            // 执行翻转
                            String newOp = getInverseBranchOp(branchParts[0]);
                            if (newOp != null) {
                                // 重组指令: newOp arg1, [arg2], jumpTarget
                                StringBuilder newInst = new StringBuilder("\t" + newOp);
                                for (int k = 1; k < branchParts.length - 1; k++) {
                                    newInst.append(" ").append(branchParts[k]).append(",");
                                }
                                newInst.append(" ").append(jumpTarget);

                                result.add(newInst + " # [opt] inverted branch");
                                result.add(lines.get(i + 1)); // nop

                                // 跳过原来的 j 指令 (i+2) 和它的 nop (i+3)
                                result.add("# [opt] removed j due to branch inversion");
                                i += 3;
                                continue;
                            }
                        }
                    }
                }
            }
            result.add(line);
        }
        return result;
    }

    private static String getInverseBranchOp(String op) {
        return switch (op) {
            case "beq" -> "bne";
            case "bne" -> "beq";
            case "beqz" -> "bnez";
            case "bnez" -> "beqz";
            default -> null;
        };
    }

    /**
     * 优化：代数化简 (add 0, mul 1, sub same)
     */
    private static List<String> simplifyAlgebra(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            String[] parts = trimmed.split("[\\s,]+");

            boolean skip = false;

            if (parts.length >= 3) {
                String op = parts[0];
                String rd = parts[1];
                String rs = parts[2];
                String rt = (parts.length > 3) ? parts[3] : "";

                // addu $t0, $t0, 0
                if ((op.equals("addu") || op.equals("add") || op.equals("subu") || op.equals("sub"))
                        && rt.equals("0")) {
                    if (rd.equals(rs)) {
                        skip = true;
                    } else {
                        // add $t0, $t1, 0 -> move $t0, $t1
                        result.add("\tmove " + rd + ", " + rs + " # [opt] simplified add 0");
                        skip = true;
                    }
                }
                // mul $t0, $t1, 1 -> move $t0, $t1
                // sub $t0, $t1, $t1 -> move $t0, $zero
            }

            if (!skip) {
                result.add(line);
            }
        }
        return result;
    }
}