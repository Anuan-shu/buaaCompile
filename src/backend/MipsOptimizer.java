package backend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            current = simplifyLi(current);
            current = mergeLiAddu(current);
            current = copyPropagation(current);

            // 2. 内存优化
            for (int i = 0; i < 4; i++) {
                current = removeRedundantLoad(current);
            }
            current = removeRedundantStore(current);
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
                    } else {
                        // 情况B: sw $t0, ...; lw $t1, ...
                        // 替换为 move $t1, $t0
                        String moveInst = "\tmove " + currentReg + ", " + lastReg;
                        result.add(moveInst);
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
                    } else {
                        // lw $t0, addr; lw $t1, addr -> move $t1, $t0
                        String moveInst = "\tmove " + currentReg + ", " + lastReg;
                        result.add(moveInst);
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
     * Label:
     * 这种跳转是多余的，直接删掉 j，让程序自然顺序执行下去。
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

                    // 向后看 1 行：
                    // i+1 应该是 TargetLabel:
                    if (i + 2 < lines.size()) {
                        String nextLine = lines.get(i + 1).trim();

                        if (nextLine.equals(targetLabel + ":")) {
                            // 命中模式
                            isRedundant = true;
                        }
                    }
                }
            }

            if (isRedundant) {
                continue;
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

            boolean optimized = false; // 标记是否发生了优化

            if (trimmed.startsWith("beq") || trimmed.startsWith("bne") ||
                    trimmed.startsWith("beqz") || trimmed.startsWith("bnez")) {

                // 检查是否有足够的行数 (Branch, Jump, Label)
                if (i + 2 < lines.size()) {
                    String jumpLine = lines.get(i + 1).trim();
                    String labelLine = lines.get(i + 2).trim();

                    if (jumpLine.startsWith("j ") && labelLine.endsWith(":")) {
                        String[] branchParts = trimmed.split("[\\s,]+");
                        String branchTarget = branchParts[branchParts.length - 1];
                        String currentLabel = labelLine.substring(0, labelLine.length() - 1);

                        // 核心判断：分支的目标 是否等于 紧随 Jump 后的 Label
                        if (branchTarget.equals(currentLabel)) {
                            String jumpTarget = jumpLine.split("\\s+")[1];
                            String newOp = getInverseBranchOp(branchParts[0]);

                            if (newOp != null) {
                                // 1. 构建反转指令
                                StringBuilder newInst = new StringBuilder("\t" + newOp);
                                for (int k = 1; k < branchParts.length - 1; k++) {
                                    newInst.append(" ").append(branchParts[k]).append(",");
                                }
                                newInst.append(" ").append(jumpTarget); // 跳转到原来的 Jump 目标

                                // 2. 添加反转后的指令
                                result.add(newInst.toString());

                                // 3. 跳过原来的 'j' 指令
                                // 只跳过 i+1 (Jump)。
                                // Label (i+2) 需要被保留，会在下一次循环中被 result.add(line) 正常添加。
                                i++;

                                optimized = true;
                            }
                        }
                    }
                }
            }

            // 如果没有发生优化，则添加原行
            // 下一次循环会自动处理 label
            if (!optimized) {
                result.add(line);
            }
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
            String replacement = null;

            if (parts.length >= 3) {
                String op = parts[0];
                String rd = parts[1];
                String rs = parts[2];
                String rt = (parts.length > 3) ? parts[3] : "";

                // addu/add/subu/sub $t0, $t1, 0 -> move $t0, $t1 or skip if same
                if ((op.equals("addu") || op.equals("add") || op.equals("addi") || op.equals("addiu") ||
                        op.equals("subu") || op.equals("sub"))
                        && rt.equals("0")) {
                    if (rd.equals(rs)) {
                        skip = true; // add $t0, $t0, 0 -> skip
                    } else {
                        replacement = "\tmove " + rd + ", " + rs;
                    }
                }
                // mul $t0, $rs, 1 -> move $t0, $rs
                else if (op.equals("mul") && rt.equals("1")) {
                    if (rd.equals(rs)) {
                        skip = true;
                    } else {
                        replacement = "\tmove " + rd + ", " + rs;
                    }
                }
                // mul $t0, $rs, 0 -> move $t0, $zero
                else if (op.equals("mul") && rt.equals("0")) {
                    replacement = "\tmove " + rd + ", $zero";
                }
                // sub $t0, $t1, $t1 -> move $t0, $zero
                else if ((op.equals("sub") || op.equals("subu")) && rs.equals(rt)) {
                    replacement = "\tmove " + rd + ", $zero";
                }
                // sll/srl $t0, $t1, 0 -> move $t0, $t1 or skip
                else if ((op.equals("sll") || op.equals("srl") || op.equals("sra")) && rt.equals("0")) {
                    if (rd.equals(rs)) {
                        skip = true;
                    } else {
                        replacement = "\tmove " + rd + ", " + rs;
                    }
                }
            }

            if (skip) {
                continue;
            } else if (replacement != null) {
                result.add(replacement);
            } else {
                result.add(line);
            }
        }
        return result;
    }


    /**
     * 优化：简化 li 指令
     * li $t0, 0 -> move $t0, $zero
     */
    private static List<String> simplifyLi(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("li ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length == 3 && parts[2].equals("0")) {
                    // li $t0, 0 -> move $t0, $zero
                    result.add("\tmove " + parts[1] + ", $zero");
                    continue;
                }
            }
            result.add(line);
        }
        return result;
    }

    /**
     * 优化：消除冗余的 Store 指令
     * 只删除严格连续的两个 sw 写同一地址的情况
     * sw $t0, addr
     * sw $t1, addr <- 上一个 sw 是死代码
     */
    private static List<String> removeRedundantStore(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            String[] parts = trimmed.split("[\\s,]+");

            // 检查是否是 sw 指令
            if (parts.length >= 3 && parts[0].equals("sw") && parts[2].contains("($")) {
                String addr = parts[2];

                // 检查下一条是否也是写同一地址的 sw
                if (i + 1 < lines.size()) {
                    String nextTrimmed = lines.get(i + 1).trim();
                    String[] nextParts = nextTrimmed.split("[\\s,]+");

                    if (nextParts.length >= 3 && nextParts[0].equals("sw") &&
                            nextParts[2].equals(addr)) {
                        // 当前 sw 是死代码，跳过不添加
                        continue;
                    }
                }
            }
            result.add(lines.get(i));
        }
        return result;
    }

    /**
     * 优化：合并 li + addu 为 addiu
     * li $t0, imm
     * addu $t1, $t2, $t0 -> addiu $t1, $t2, imm (if imm fits in 16 bits)
     */
    private static List<String> mergeLiAddu(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();

            // 检查是否是 li 指令
            if (trimmed.startsWith("li ")) {
                String[] liParts = trimmed.split("[\\s,]+");
                if (liParts.length == 3) {
                    String liReg = liParts[1];
                    String immStr = liParts[2];

                    try {
                        int imm;
                        if (immStr.startsWith("0x") || immStr.startsWith("0X")) {
                            imm = Integer.parseInt(immStr.substring(2), 16);
                        } else {
                            imm = Integer.parseInt(immStr);
                        }

                        // 检查下一条是否是使用这个寄存器的 addu
                        if (i + 1 < lines.size() && imm >= -32768 && imm <= 32767) {
                            String nextTrimmed = lines.get(i + 1).trim();
                            String[] adduParts = nextTrimmed.split("[\\s,]+");

                            if (adduParts.length == 4 && adduParts[0].equals("addu")) {
                                String destReg = adduParts[1];
                                String srcReg1 = adduParts[2];
                                String srcReg2 = adduParts[3];

                                // 检查 liReg 是 addu 的第二个或第三个操作数
                                if (srcReg2.equals(liReg) && !srcReg1.equals(liReg)) {
                                    // addu $d, $s, $liReg -> addiu $d, $s, imm
                                    result.add(String.format("\taddiu %s, %s, %d", destReg, srcReg1, imm));
                                    i++; // 跳过 addu
                                    continue;
                                } else if (srcReg1.equals(liReg) && !srcReg2.equals(liReg)) {
                                    // addu $d, $liReg, $s -> addiu $d, $s, imm
                                    result.add(String.format("\taddiu %s, %s, %d", destReg, srcReg2, imm));
                                    i++; // 跳过 addu
                                    continue;
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无法解析的立即数
                    }
                }
            }
            result.add(lines.get(i));
        }
        return result;
    }

    /**
     * 拷贝传播
     * 
     * 只有当 move 的目的寄存器在后续代码中不再被使用时，才能消除 move
     */
    private static List<String> copyPropagation(List<String> lines) {
        List<String> result = new ArrayList<>();
        Set<String> safeOps = new HashSet<>();
        safeOps.add("addu");
        safeOps.add("subu");
        safeOps.add("and");
        safeOps.add("or");
        safeOps.add("xor");
        safeOps.add("slt");
        safeOps.add("sltu");
        safeOps.add("sll");
        safeOps.add("srl");
        safeOps.add("sra");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // 只处理 move 指令
            if (!trimmed.startsWith("move ")) {
                result.add(line);
                continue;
            }

            String[] moveParts = trimmed.split("[\\s,]+");
            if (moveParts.length != 3) {
                result.add(line);
                continue;
            }

            String moveDest = moveParts[1]; // move 的目的寄存器
            String moveSrc = moveParts[2]; // move 的源寄存器

            // 检查下一条指令
            if (i + 1 >= lines.size()) {
                result.add(line);
                continue;
            }

            String nextLine = lines.get(i + 1);
            String nextTrimmed = nextLine.trim();
            String[] nextParts = nextTrimmed.split("[\\s,]+");

            // 必须是 4 个部分: OP $dest, $src1, $src2
            if (nextParts.length != 4) {
                result.add(line);
                continue;
            }

            String op = nextParts[0];
            String dest = nextParts[1];
            String src1 = nextParts[2];
            String src2 = nextParts[3];

            // 必须是安全的操作
            if (!safeOps.contains(op)) {
                result.add(line);
                continue;
            }

            // moveDest 不能是这条指令的 dest
            if (dest.equals(moveDest)) {
                result.add(line);
                continue;
            }

            // 检查 moveDest 在哪里被使用
            boolean usedInSrc1 = src1.equals(moveDest);
            boolean usedInSrc2 = src2.equals(moveDest);

            // 必须恰好使用一次
            if (usedInSrc1 && usedInSrc2) {
                result.add(line);
                continue;
            }
            if (!usedInSrc1 && !usedInSrc2) {
                result.add(line);
                continue;
            }

            // 检查 moveDest 在后续指令中是否还被使用
            boolean usedLater = isRegisterUsedLater(lines, i + 2, moveDest);
            if (usedLater) {
                // moveDest 在后面还有用，不能消除 move
                result.add(line);
                continue;
            }

            // 可以安全地替换并跳过 move
            String newSrc1 = usedInSrc1 ? moveSrc : src1;
            String newSrc2 = usedInSrc2 ? moveSrc : src2;
            String newInst = String.format("\t%s %s, %s, %s", op, dest, newSrc1, newSrc2);
            result.add(newInst);
            i++; // 跳过原来的下一行
        }
        return result;
    }

    /**
     * 检查寄存器在后续指令中是否还被使用
     * 扫描直到遇到：1) 标签 2) 跳转/分支 3) 寄存器被重新定义
     */
    private static boolean isRegisterUsedLater(List<String> lines, int startIdx, String reg) {
        for (int i = startIdx; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();

            // 遇到标签就停止 - 不知道控制流
            if (trimmed.endsWith(":")) {
                return true; // 保守假设：可能被使用
            }

            // 遇到跳转/分支就停止
            if (trimmed.startsWith("j") || trimmed.startsWith("b") ||
                    trimmed.equals("syscall") || trimmed.startsWith("jr")) {
                return true; // 保守假设
            }

            String[] parts = trimmed.split("[\\s,]+");
            if (parts.length == 0)
                continue;

            // 检查是否在这条指令中作为源操作数被使用
            for (int j = 2; j < parts.length; j++) {
                if (parts[j].equals(reg)) {
                    return true; // 被使用了
                }
                // 检查内存操作数 offset($reg)
                if (parts[j].contains("(" + reg + ")")) {
                    return true;
                }
            }

            // 检查是否被重新定义（作为目的寄存器）
            if (parts.length >= 2 && parts[1].equals(reg)) {
                return false; // 在被使用之前就被重新定义了，之前的值不再需要
            }
        }
        return false; // 扫描完没找到使用
    }
}