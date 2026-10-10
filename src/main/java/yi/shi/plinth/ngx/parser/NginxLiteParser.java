package yi.shi.plinth.ngx.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 nginx 配置解析器：按行逐条解析，栈式处理 {} 形成 AST。
 *
 * <p>刻意保持简单（不追求 100% 规范）：
 * <ul>
 *   <li>注释行剥除；单引号/双引号内空白为一个 token；支持 `} ` 与参数同行（块尾合并行）。</li>
 *   <li>一行的最后一个 '{' 视为块开（location 的正则 args 中含 { 的极端写法以含尾参行为准）。</li>
 *   <li>不支持行内多指令一行一语句之外的 `server { listen 80; }` 完全单行写法（出现时按普通
 *       参数块解析，子指令会丢失——属于可接受近似，自动布置的配置一般不会这样写）。</li>
 * </ul>
 */
public final class NginxLiteParser {

    /** 配置节点：指令（children==null）或块（children 非 null）。 */
    public static final class CfgNode {
        public final String name;
        public final List<String> args;
        public final List<CfgNode> children; // 块专属；指令为 null
        public final int line;
        public final String file;

        public CfgNode(String name, List<String> args, List<CfgNode> children, int line, String file) {
            this.name = name;
            this.args = args;
            this.children = children;
            this.line = line;
            this.file = file;
        }

        public String arg(int i) { return args != null && args.size() > i ? args.get(i) : null; }
        /** 递归查找指定名称的（首层匹配：所有子树）块节点。 */
        public List<CfgNode> findBlocks(String blockName) {
            List<CfgNode> out = new ArrayList<>();
            collect(children, blockName, out);
            return out;
        }
        private static void collect(List<CfgNode> nodes, String bn, List<CfgNode> out) {
            if (nodes == null) { return; }
            for (CfgNode n : nodes) {
                if (bn.equals(n.name) && n.children != null) { out.add(n); }
                collect(n.children, bn, out);
            }
        }
        /** 首层指令取值（name 相同的第一条 arg(i)）。 */
        public String firstArg(String directiveName, int idx) {
            if (children == null) { return null; }
            for (CfgNode n : children) {
                if (directiveName.equals(n.name) && n.children == null) {
                    return n.arg(idx);
                }
            }
            return null;
        }
        /** 首层所有指定名称块/指令。 */
        public List<CfgNode> direct(String name) {
            List<CfgNode> out = new ArrayList<>();
            if (children != null) {
                for (CfgNode n : children) {
                    if (name.equals(n.name)) { out.add(n); }
                }
            }
            return out;
        }
        /** 是否为"指令"（非块）。块内 server 这类同名词需区分。 */
        public boolean isDirective() { return children == null; }
    }

    /** 解析单文件文本 → 顶层节点列表。语法异常行按普通 token 兜底，不抛出。 */
    public static List<CfgNode> parse(String text, String fileName) {
        List<CfgNode> root = new ArrayList<>();
        List<CfgNode> stack = new ArrayList<>();
        int lineNo = 0;
        for (String rawLine : text.split("\r?\n", -1)) {
            lineNo++;
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) { continue; }

            // 块闭合（可能行首/行尾）
            while (line.startsWith("}")) {
                if (!stack.isEmpty()) { stack.remove(stack.size() - 1); }
                line = line.substring(1).trim();
                if (line.isEmpty()) { continue; }
            }

            int braceIdx = line.lastIndexOf('{');
            if (braceIdx >= 0 && !inQuotes(line, braceIdx)) {
                // 块打开：{ 前的 tokens 为块头；} 若同行（如 `} 后跟子指令`）其余继续处理近似
                String head = line.substring(0, braceIdx).trim();
                List<String> toks = tokenize(head);
                if (toks.isEmpty()) { continue; }
                String parentFile = stack.isEmpty() ? fileName : fileName;
                CfgNode node = new CfgNode(blockName(toks), toks.subList(1, toks.size()),
                        new ArrayList<>(), lineNo, parentFile);
                addTo(stack, root, node);
                stack.add(node);
                String tail = line.substring(braceIdx + 1).trim();
                if (tail.startsWith("}")) {
                    // `xxx { }` 单行块：立即出栈
                    if (!stack.isEmpty()) { stack.remove(stack.size() - 1); }
                    tail = tail.substring(1).trim();
                }
                if (!tail.isEmpty() && !tail.startsWith("}")) {
                    // 行尾残留指令（罕见），按当前栈顶指令兜底
                    var cur = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                    if (cur != null && cur.children != null) {
                        for (String t : tokenize(tail)) {
                            cur.children.add(new CfgNode(t, List.of(), null, lineNo, fileName));
                        }
                    }
                }
                continue;
            }
            // 普通指令
            List<String> toks = tokenize(line);
            if (toks.isEmpty()) { continue; }
            CfgNode node = new CfgNode(normDirective(toks.get(0)),
                    toks.subList(1, toks.size()), null, lineNo, fileName);
            addTo(stack, root, node);
        }
        return root;
    }

    private static void addTo(List<CfgNode> stack, List<CfgNode> root, CfgNode node) {
        if (stack.isEmpty()) { root.add(node); }
        else {
            CfgNode top = stack.get(stack.size() - 1);
            if (top.children != null) { top.children.add(node); }
            else { root.add(node); }
        }
    }

    /** 块头：server/listen… 均转小写；首 token 之后的可能路径参数保留原样。 */
    private static String blockName(List<String> toks) {
        return toks.get(0).toLowerCase();
    }
    private static String normDirective(String name) {
        return name.startsWith("$") ? name : name.toLowerCase();
    }

    /** 去掉行内 '#…' 注释（引号内的 # 保留）。 */
    private static String stripComment(String line) {
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') { q = !q; }
            if (c == '#' && !q) { return line.substring(0, i); }
        }
        return line;
    }

    /** 支持 quoted token 的分词。 */
    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) { i++; }
            if (i >= n) { break; }
            StringBuilder b = new StringBuilder();
            if (s.charAt(i) == '"' || s.charAt(i) == '\'') {
                char qc = s.charAt(i++);
                while (i < n && s.charAt(i) != qc) { b.append(s.charAt(i++)); }
                if (i < n) { i++; }
            } else {
                while (i < n && !Character.isWhitespace(s.charAt(i))) { b.append(s.charAt(i++)); }
            }
            String tok = b.toString();
            // 指令行以 ';' 结尾（nginx 语法），把分号从 token 尾巴剥掉，否则参数匹配丢判
            while (tok.endsWith(";") && !tok.equals(";")) { tok = tok.substring(0, tok.length() - 1); }
            if (tok.length() > 0) { out.add(tok); }
        }
        return out;
    }

    private static boolean inQuotes(String line, int idx) {
        boolean q = false;
        for (int i = 0; i < idx; i++) {
            if (line.charAt(i) == '"' || line.charAt(i) == '\'') { q = !q; }
        }
        return q;
    }

    private NginxLiteParser() { }
}
