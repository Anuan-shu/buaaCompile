package backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            for (int j = 0; j < 8; j++) { // 增加到 8 次
                current = removeRedundantMoves(current);
                current = removeMoveChains(current);
                current = copyPropagation(current); // 每轮都做 copy prop
            }
            current = simplifyAlgebra(current);
            current = simplifyLi(current);
            current = mergeLiAddu(current);

            // 1.5 消除无用栈操作
            current = removeUselessStackOps(current);

            // 1.6 MIPS 级别死代码消除
            current = mipsDeadCodeElimination(current);

            // 2. 内存优化 - 多次迭代以更彻底消除
            for (int i = 0; i < 8; i++) {
                current = removeRedundantLoad(current);
                current = removeRedundantStore(current);
                current = deadStoreElimination(current);
            }
            current = aggressiveMemoryElimination(current);

            // 2.5 地址加载优化
            current = fuseLaLw(current);

            // 3. 分支优化
            current = optimizeBranches(current);
            current = removeRedundantJumps(current);

            // 4. 指令选择优化
            current = betterInstructionSelection(current);

            // 5. 消除冗余比较
            current = removeRedundantCompare(current);

            // 5.5 消除冗余 li
            current = eliminateRedundantLi(current);

            // 5.6 简单指令调度 - 减少 load-use 延迟
            current = scheduleInstructions(current);

            // 6. 最后再次清理 move
            for (int j = 0; j < 8; j++) { // 增加到 8 次
                current = removeRedundantMoves(current);
                current = removeMoveChains(current);
                current = copyPropagation(current);
            }

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
     * 死存储消除：删除被后续覆盖的 sw
     * 模式：
     * sw $t0, offset($fp)
     * ... (没有 lw 从这个地址读取)
     * sw $t1, offset($fp) <- 第一个 sw 是死存储
     */
    private static List<String> deadStoreElimination(List<String> lines) {
        List<String> result = new ArrayList<>();

        // 记录每个地址最后一次 sw 的索引
        Map<String, Integer> lastStoreIdx = new HashMap<>();
        // 记录哪些索引应该被删除
        Set<Integer> toRemove = new HashSet<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            String[] parts = trimmed.split("[\\s,]+");

            // 遇到标签或分支，清空状态
            if (trimmed.endsWith(":") || trimmed.startsWith("b") ||
                    trimmed.startsWith("j") || trimmed.equals("syscall")) {
                lastStoreIdx.clear();
                continue;
            }

            if (parts.length == 3 && parts[0].equals("sw") && parts[2].contains("($fp)")) {
                String addr = parts[2];
                if (lastStoreIdx.containsKey(addr)) {
                    // 之前有一个 sw 到同一地址，且中间没有 lw，标记为删除
                    toRemove.add(lastStoreIdx.get(addr));
                }
                lastStoreIdx.put(addr, i);
            } else if (parts.length == 3 && parts[0].equals("lw") && parts[2].contains("($fp)")) {
                // lw 会使用这个地址的值，所以之前的 sw 不是死存储
                String addr = parts[2];
                lastStoreIdx.remove(addr);
            }
        }

        // 构建结果，跳过死存储
        for (int i = 0; i < lines.size(); i++) {
            if (!toRemove.contains(i)) {
                result.add(lines.get(i));
            }
        }
        return result;
    }

    /**
     * 激进内存消除：追踪寄存器中已有的值，消除冗余 lw
     * 只在 lw 之后建立追踪，sw 不建立新追踪（因为源寄存器可能被修改）
     */
    private static List<String> aggressiveMemoryElimination(List<String> lines) {
        List<String> result = new ArrayList<>();

        // 追踪：地址 -> 持有该地址值的寄存器
        Map<String, String> addrToReg = new HashMap<>();
        // 追踪：寄存器 -> 它持有的地址（反向映射）
        Map<String, String> regToAddr = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // 空行直接添加
            if (trimmed.isEmpty()) {
                result.add(line);
                continue;
            }

            String[] parts = trimmed.split("[\\s,]+");

            // 遇到标签、分支、跳转、syscall、jal，清空状态
            if (trimmed.endsWith(":") || trimmed.startsWith("b") ||
                    trimmed.startsWith("j") || trimmed.equals("syscall") ||
                    trimmed.startsWith("#") || parts[0].equals("jal") ||
                    parts[0].equals("jr") || parts[0].equals("jalr")) {
                addrToReg.clear();
                regToAddr.clear();
                result.add(line);
                continue;
            }

            boolean replaced = false;

            if (parts.length == 3 && parts[0].equals("lw") && parts[2].contains("($fp)")) {
                String reg = parts[1];
                String addr = parts[2];

                // 先使目标寄存器的旧映射失效
                invalidateReg(reg, addrToReg, regToAddr);

                // 如果这个地址的值已经在某个寄存器中
                if (addrToReg.containsKey(addr)) {
                    String srcReg = addrToReg.get(addr);
                    if (srcReg.equals(reg)) {
                        // lw 到同一个寄存器，值没变，跳过
                        replaced = true;
                    } else {
                        // lw 到不同寄存器，用 move 替换
                        result.add("\tmove " + reg + ", " + srcReg);
                        replaced = true;
                        // 新寄存器也持有这个地址的值
                        addrToReg.put(addr, reg);
                        regToAddr.put(reg, addr);
                    }
                }

                if (!replaced) {
                    // lw 成功执行后，建立追踪
                    addrToReg.put(addr, reg);
                    regToAddr.put(reg, addr);
                }
            } else if (parts.length == 3 && parts[0].equals("sw") && parts[2].contains("($fp)")) {
                String reg = parts[1];
                String addr = parts[2];

                // sw 后，这个地址有新值了
                // 清除该地址的旧映射（因为值可能变了）
                if (addrToReg.containsKey(addr)) {
                    String oldReg = addrToReg.get(addr);
                    if (!oldReg.equals(reg)) {
                        // 如果存入的寄存器和之前追踪的不同，清除旧追踪
                        regToAddr.remove(oldReg);
                    }
                }
                // sw 之后，这个地址的值就是这个寄存器的值
                addrToReg.put(addr, reg);
                regToAddr.put(reg, addr);
            } else if (parts.length >= 2 && !parts[0].equals("sw") && !parts[0].equals("lw")) {
                // 其他指令可能修改寄存器，使追踪失效
                String destReg = parts[1];
                if (destReg.startsWith("$")) {
                    invalidateReg(destReg, addrToReg, regToAddr);
                }
            }

            if (!replaced) {
                result.add(line);
            }
        }

        return result;
    }

    private static void invalidateReg(String reg, Map<String, String> addrToReg, Map<String, String> regToAddr) {
        if (regToAddr.containsKey(reg)) {
            String addr = regToAddr.get(reg);
            // 只有当这个地址映射到这个寄存器时才清除
            if (addrToReg.get(addr) != null && addrToReg.get(addr).equals(reg)) {
                addrToReg.remove(addr);
            }
            regToAddr.remove(reg);
        }
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
     * 优化：删除冗余的 move 指令
     * 1. move $t0, $t0 -> 删除
     * 2. move $t1, $t0; move $t0, $t1 (如果 $t1 之后不再使用) -> 删除两条
     */
    private static List<String> removeRedundantMoves(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.startsWith("move ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length == 3) {
                    String dest = parts[1];
                    String src = parts[2];

                    // move $t0, $t0 -> 删除
                    if (dest.equals(src)) {
                        continue;
                    }

                    // move $t1, $t0; move $t0, $t1 -> 如果 $t1 之后不被使用，删除两条
                    if (i + 1 < lines.size()) {
                        String nextTrimmed = lines.get(i + 1).trim();
                        String[] nextParts = nextTrimmed.split("[\\s,]+");
                        if (nextParts.length == 3 && nextParts[0].equals("move") &&
                                nextParts[1].equals(src) && nextParts[2].equals(dest)) {
                            // 检查 dest 在 i+2 之后是否被使用
                            if (!isRegUsedLater(lines, i + 2, dest)) {
                                // $t1 (dest) 之后不再使用，可以安全删除两条
                                i++; // 跳过下一条
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

    /**
     * 检查寄存器在指定位置之后是否被使用（作为源操作数）
     */
    private static boolean isRegUsedLater(List<String> lines, int startIdx, String reg) {
        for (int i = startIdx; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();

            // 遇到标签，继续检查（控制流可能跳转）
            if (trimmed.endsWith(":"))
                continue;

            // 遇到跳转或分支，保守返回 true（寄存器可能在其他地方使用）
            if (trimmed.startsWith("j ") || trimmed.startsWith("jal") ||
                    trimmed.startsWith("jr") || trimmed.startsWith("b")) {
                return true;
            }

            String[] parts = trimmed.split("[\\s,]+");
            if (parts.length < 2)
                continue;

            // 检查是否作为源操作数使用
            for (int j = 2; j < parts.length; j++) {
                String operand = parts[j];
                // 处理 offset($reg) 格式
                if (operand.contains("(" + reg + ")") || operand.equals(reg)) {
                    return true;
                }
            }

            // 如果寄存器被重新定义（作为目的操作数），之后的值与之前无关
            if (parts.length >= 2 && parts[1].equals(reg)) {
                return false;
            }
        }
        return false;
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
                // mul $t0, $rs, 2 -> sll $t0, $rs, 1
                else if (op.equals("mul") && rt.equals("2")) {
                    replacement = "\tsll " + rd + ", " + rs + ", 1";
                }
                // mul $t0, $rs, 4 -> sll $t0, $rs, 2
                else if (op.equals("mul") && rt.equals("4")) {
                    replacement = "\tsll " + rd + ", " + rs + ", 2";
                }
                // mul $t0, $rs, 8 -> sll $t0, $rs, 3
                else if (op.equals("mul") && rt.equals("8")) {
                    replacement = "\tsll " + rd + ", " + rs + ", 3";
                }
                // mul $t0, $rs, 16 -> sll $t0, $rs, 4
                else if (op.equals("mul") && rt.equals("16")) {
                    replacement = "\tsll " + rd + ", " + rs + ", 4";
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
                // and $t0, $rs, 0 -> move $t0, $zero
                else if (op.equals("and") && (rt.equals("0") || rt.equals("$zero"))) {
                    replacement = "\tmove " + rd + ", $zero";
                }
                // or $t0, $rs, 0 -> move $t0, $rs
                else if (op.equals("or") && (rt.equals("0") || rt.equals("$zero"))) {
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
                            String[] opParts = nextTrimmed.split("[\\s,]+");

                            if (opParts.length == 4 && opParts[0].equals("addu")) {
                                String destReg = opParts[1];
                                String srcReg1 = opParts[2];
                                String srcReg2 = opParts[3];

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
                            // 新增: li + subu -> addiu with negative
                            else if (opParts.length == 4 && opParts[0].equals("subu")) {
                                String destReg = opParts[1];
                                String srcReg1 = opParts[2];
                                String srcReg2 = opParts[3];

                                // subu $d, $s, $liReg -> addiu $d, $s, -imm
                                if (srcReg2.equals(liReg) && !srcReg1.equals(liReg) && -imm >= -32768
                                        && -imm <= 32767) {
                                    result.add(String.format("\taddiu %s, %s, %d", destReg, srcReg1, -imm));
                                    i++; // 跳过 subu
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

    /**
     * 更好的指令选择优化
     */
    private static List<String> betterInstructionSelection(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            String[] parts = trimmed.split("[\\s,]+");

            boolean skip = false;
            String replacement = null;

            // 优化 1: beq $reg, $zero, label -> beqz $reg, label
            if (parts.length == 4 && parts[0].equals("beq") && parts[2].equals("$zero")) {
                replacement = "\tbeqz " + parts[1] + ", " + parts[3];
            }
            // 优化 2: bne $reg, $zero, label -> bnez $reg, label
            else if (parts.length == 4 && parts[0].equals("bne") && parts[2].equals("$zero")) {
                replacement = "\tbnez " + parts[1] + ", " + parts[3];
            }
            // 优化 3: li $t0, 0 -> move $t0, $zero
            else if (parts.length == 3 && parts[0].equals("li") && parts[2].equals("0")) {
                replacement = "\tmove " + parts[1] + ", $zero";
            }
            // 优化 4: addu $rd, $rs, $zero -> move $rd, $rs
            else if (parts.length == 4 && parts[0].equals("addu") && parts[3].equals("$zero")) {
                if (!parts[1].equals(parts[2])) {
                    replacement = "\tmove " + parts[1] + ", " + parts[2];
                } else {
                    skip = true; // addu $t0, $t0, $zero -> 无用
                }
            }
            // 优化 5: addu $rd, $zero, $rs -> move $rd, $rs
            else if (parts.length == 4 && parts[0].equals("addu") && parts[2].equals("$zero")) {
                if (!parts[1].equals(parts[3])) {
                    replacement = "\tmove " + parts[1] + ", " + parts[3];
                } else {
                    skip = true;
                }
            }
            // 优化 6: or $rd, $rs, $zero -> move $rd, $rs
            else if (parts.length == 4 && parts[0].equals("or") && parts[3].equals("$zero")) {
                if (!parts[1].equals(parts[2])) {
                    replacement = "\tmove " + parts[1] + ", " + parts[2];
                } else {
                    skip = true;
                }
            }
            // 优化 7: li $t0, small; addu $t1, $t2, $t0 -> addiu $t1, $t2, small
            // (已在 mergeLiAddu 中处理)

            // 优化 8: 删除跳转到下一行的 j 指令
            else if (parts.length == 2 && parts[0].equals("j")) {
                String target = parts[1];
                if (i + 1 < lines.size()) {
                    String nextLine = lines.get(i + 1).trim();
                    if (nextLine.equals(target + ":")) {
                        skip = true; // j label 后面紧跟 label: -> 删除 j
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
     * 消除 move 链：move $t3, $t0; move $t0, $t3
     * 第二条 move 是 no-op（$t0 已经有自己的值），只删除它
     * 保留第一条 move（$t3 需要获得 $t0 的值）
     */
    private static List<String> removeMoveChains(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.startsWith("move ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length == 3) {
                    String dest = parts[1];
                    String src = parts[2];

                    // 检查下一条是否是反向 move
                    if (i + 1 < lines.size()) {
                        String next = lines.get(i + 1).trim();
                        String[] nextParts = next.split("[\\s,]+");
                        if (nextParts.length == 3 && nextParts[0].equals("move") &&
                                nextParts[1].equals(src) && nextParts[2].equals(dest)) {
                            // move $t3, $t0; move $t0, $t3
                            // 保留第一条 (result.add(line))
                            // 跳过第二条 (它是 no-op)
                            result.add(line);
                            i++; // 跳过下一条
                            continue;
                        }

                        // 前向传播: move $A, $B; move $C, $A -> move $C, $B
                        // 条件: $A 之后不再使用
                        if (nextParts[0].equals("move") && nextParts[2].equals(dest)) {
                            String nextDest = nextParts[1]; // $C
                            // dest = $A, src = $B, nextDest = $C
                            // 如果 $A 之后不再使用，可以跳过第一条 move
                            if (!isRegisterUsedLater(lines, i + 2, dest)) {
                                // 直接生成 move $C, $B，跳过两条原指令
                                if (nextDest.equals(src)) {
                                    // move $A, $B; move $B, $A 变成 nop
                                    i++; // 跳过下一条
                                    continue;
                                }
                                result.add("\tmove " + nextDest + ", " + src);
                                i++; // 跳过下一条
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

    /**
     * 消除无用栈操作
     * addiu $sp, $sp, -4; sw $t0, 0($sp); lw $rx, 0($sp); addiu $sp, $sp, 4 -> move
     * $rx, $t0
     */
    private static List<String> removeUselessStackOps(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // 检测模式：addiu $sp, $sp, -4
            if (trimmed.equals("addiu $sp, $sp, -4") && i + 3 < lines.size()) {
                String sw = lines.get(i + 1).trim();
                String lw = lines.get(i + 2).trim();
                String addBack = lines.get(i + 3).trim();

                // sw $tX, 0($sp)
                if (sw.startsWith("sw ") && sw.contains("0($sp)") &&
                        lw.startsWith("lw ") && lw.contains("0($sp)") &&
                        addBack.equals("addiu $sp, $sp, 4")) {

                    String[] swParts = sw.split("[\\s,]+");
                    String[] lwParts = lw.split("[\\s,]+");
                    if (swParts.length >= 2 && lwParts.length >= 2) {
                        String srcReg = swParts[1];
                        String destReg = lwParts[1];

                        if (srcReg.equals(destReg)) {
                            // 同一寄存器，直接删除所有 4 条
                            i += 3;
                            continue;
                        } else {
                            // 不同寄存器，替换为 move
                            result.add("\tmove " + destReg + ", " + srcReg);
                            i += 3;
                            continue;
                        }
                    }
                }
            }
            result.add(line);
        }
        return result;
    }

    /**
     * 消除冗余比较：xor $t, $t, $zero; sltu $t, $zero, $t -> bnez 可以直接使用原值
     * 因为 (bool != 0) == bool
     */
    private static List<String> removeRedundantCompare(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // 检测：move $t1, $zero 或 li $t1, 0
            if ((trimmed.startsWith("move ") && trimmed.contains("$zero")) ||
                    (trimmed.startsWith("li ") && trimmed.endsWith(", 0"))) {

                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 2 && i + 1 < lines.size()) {
                    String zeroReg = parts[1];
                    String next = lines.get(i + 1).trim();

                    // xor $t2, $t0, $zeroReg
                    if (next.startsWith("xor ") && next.contains(zeroReg)) {
                        String[] xorParts = next.split("[\\s,]+");
                        if (xorParts.length == 4 &&
                                (xorParts[3].equals(zeroReg) || xorParts[2].equals(zeroReg))) {
                            // xor $dest, $src, $zero = move $dest, $src
                            String dest = xorParts[1];
                            String src = xorParts[2].equals(zeroReg) ? xorParts[3] : xorParts[2];

                            // 跳过 move $t1, $zero
                            // 替换 xor 为 move
                            result.add("\tmove " + dest + ", " + src);
                            i++; // 跳过 xor
                            continue;
                        }
                    }
                }
            }
            result.add(line);
        }
        return result;
    }

    /**
     * 合并 la + lw/sw 为直接寻址
     * la $t1, label; lw $t0, 0($t1) -> lw $t0, label
     * la $t1, label; sw $t0, 0($t1) -> sw $t0, label
     */
    private static List<String> fuseLaLw(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // la $t1, label
            if (trimmed.startsWith("la ") && i + 1 < lines.size()) {
                String[] laParts = trimmed.split("[\\s,]+");
                if (laParts.length == 3) {
                    String addrReg = laParts[1];
                    String label = laParts[2];

                    String next = lines.get(i + 1).trim();
                    String[] nextParts = next.split("[\\s,]+");

                    // lw $t0, 0($t1)
                    if (nextParts.length == 3 &&
                            (nextParts[0].equals("lw") || nextParts[0].equals("sw")) &&
                            nextParts[2].equals("0(" + addrReg + ")")) {

                        String op = nextParts[0];
                        String dataReg = nextParts[1];

                        // 合并为 lw/sw $t0, label
                        result.add("\t" + op + " " + dataReg + ", " + label);
                        i++; // 跳过 lw/sw
                        continue;
                    }
                }
            }
            result.add(line);
        }
        return result;
    }

    /**
     * MIPS 级别死代码消除
     * 删除定义后未使用的临时寄存器指令
     */
    private static List<String> mipsDeadCodeElimination(List<String> lines) {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // li $reg, X 后面紧跟 li $reg, Y -> 删除第一个
            // 只删除当下一条完全覆盖且不使用当前值
            if (trimmed.startsWith("li ") && i + 1 < lines.size()) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 2) {
                    String reg = parts[1];
                    String next = lines.get(i + 1).trim();
                    // 只有当下一条是 li 或 lw（完全覆盖，不读取原值）才删除
                    if (next.startsWith("li " + reg + ",") ||
                            next.startsWith("lw " + reg + ",")) {
                        // 确保 lw 的地址部分不使用 reg
                        if (!next.contains("(" + reg + ")")) {
                            continue;
                        }
                    }
                }
            }

            // move $dest, $src 后面紧跟完全覆盖 $dest -> 删除 move
            // 只考虑 li 和 lw (不使用 dest 作为源)
            if (trimmed.startsWith("move ") && i + 1 < lines.size()) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length == 3) {
                    String dest = parts[1];
                    String next = lines.get(i + 1).trim();
                    // 只有 li 完全覆盖，或 lw 且地址不用 dest
                    if (next.startsWith("li " + dest + ",")) {
                        continue;
                    }
                    if (next.startsWith("lw " + dest + ",") && !next.contains("(" + dest + ")")) {
                        continue;
                    }
                }
            }

            result.add(line);
        }
        return result;
    }

    /**
     * 消除冗余 li 指令
     * 1. li $reg, 0 -> move $reg, $zero
     * 2. 连续相同 li 值跳过（考虑标签和分支）
     */
    private static List<String> eliminateRedundantLi(List<String> lines) {
        List<String> result = new ArrayList<>();
        Map<String, Integer> lastLiValue = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // 遇到标签，清除所有跟踪（可能是分支目标）
            if (trimmed.endsWith(":")) {
                lastLiValue.clear();
                result.add(line);
                continue;
            }

            // 遇到跳转或分支，清除所有跟踪
            if (trimmed.startsWith("j ") || trimmed.startsWith("jal") ||
                    trimmed.startsWith("jr") || trimmed.startsWith("b")) {
                lastLiValue.clear();
                result.add(line);
                continue;
            }

            // 遇到 syscall，清除所有跟踪
            if (trimmed.equals("syscall")) {
                lastLiValue.clear();
                result.add(line);
                continue;
            }

            if (trimmed.startsWith("li ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length == 3) {
                    String reg = parts[1];
                    try {
                        int val = Integer.parseInt(parts[2]);

                        // 如果上次 li 到这个寄存器是同一个值，跳过
                        if (lastLiValue.containsKey(reg) && lastLiValue.get(reg) == val) {
                            continue;
                        }
                        lastLiValue.put(reg, val);

                        // li $reg, 0 -> move $reg, $zero
                        if (val == 0) {
                            result.add("\tmove " + reg + ", $zero");
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        lastLiValue.remove(reg);
                    }
                }
            } else {
                // 其他指令可能修改寄存器，清除该寄存器的跟踪
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 2 && !parts[0].startsWith("sw") && !parts[0].startsWith("sb")) {
                    // 目标寄存器通常是第一个操作数（除了 store 指令）
                    lastLiValue.remove(parts[1]);
                }
            }

            result.add(line);
        }
        return result;
    }

    /**
     * 简单指令调度 - 尝试在 lw 和使用它的指令之间插入独立指令
     * 减少 load-use 延迟
     */
    private static List<String> scheduleInstructions(List<String> lines) {
        // 简化版：如果发现 lw 后紧跟使用该寄存器的指令，
        // 尝试将 lw 往前移动（如果前面的指令是独立的）
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // 寻找模式：lw $dest, ...; 下一条立即使用 $dest
            if (trimmed.startsWith("lw ") && i + 1 < lines.size()) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 2) {
                    String dest = parts[1];
                    String next = lines.get(i + 1).trim();

                    // 下一条使用 dest 作为源操作数
                    if (next.contains(dest) && !next.startsWith("lw ") &&
                            !next.endsWith(":") && !next.startsWith("sw ")) {

                        // 检查前一条是否可以移到 lw 和 use 之间
                        if (result.size() > 0) {
                            String prev = result.get(result.size() - 1).trim();
                            // 如果前一条是独立的（li, move 到不同寄存器）
                            if ((prev.startsWith("li ") || prev.startsWith("move ")) &&
                                    !prev.contains(dest)) {
                                // 交换：lw 移到前面
                                String removed = result.remove(result.size() - 1);
                                result.add(line);
                                result.add(removed);
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
}