package yi.shi.plinth.ngx.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.ngx.NginxProperties;
import yi.shi.plinth.ngx.dto.TopologyGraph;
import yi.shi.plinth.ngx.parser.NginxLiteParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 网络拓扑分析：解析配置目录全部 nginx 配置文件，
 * 抽取 server（listen/server_name/SSL/locations）→ proxy_pass/grpc_pass/fastcgi_pass 等代理
 * → upstream / 直连后端 / 静态目录 的链接关系。
 *
 * <p>说明：阶段不考虑 include 链路精确归因（文件树全扫），upstream 与 server 同名匹配失败时会
 * 归入"未知 upstream 告警"。数据供前端渲染分层拓扑图。
 */
@Slf4j
@Singleton
public class TopologyService {

    private static final long MAX_SCAN_BYTES = 2 * 1024 * 1024;

    @Inject
    public TopologyService() { }

    public TopologyGraph analyze() {
        TopologyGraph g = new TopologyGraph();
        List<TopologyGraph.ServerNode> servers = new ArrayList<>();
        List<TopologyGraph.UpstreamNode> ups = new ArrayList<>();
        List<TopologyGraph.BackendNode> backendNodes = new ArrayList<>();
        Map<String, TopologyGraph.BackendNode> backendByKey = new LinkedHashMap<>();
        List<TopologyGraph.Edge> edges = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<NginxLiteParser.CfgNode> serverBlocks = new ArrayList<>();
        int[] files = {0};   // 解析文件数（局部，防实例字段跨请求累加）
        Map<String, TopologyGraph.UpstreamNode> upByName = new LinkedHashMap<>();
        List<String> chain = new ArrayList<>();      // 展开链：入口文件 + includes 顺序
        Set<String> visited = new LinkedHashSet<>(); // 已解析文件（按真实路径）

        // 入口：conf 主配置文件（默认 nginx.conf；不可用时按目录全扫兜底）
        Path entry = NginxProperties.confDirPath().resolve("nginx.conf");
        Deque<Path> queue = new ArrayDeque<>();
        Path confRoot = NginxProperties.confDirPath().toAbsolutePath();

        if (Files.isRegularFile(entry)) {
            queue.add(entry.toAbsolutePath());
        } else {
            warnings.add("未找到主配置 " + confRoot + "/nginx.conf，回退为目录全扫（include 链失效）");
            try {
                try (java.util.stream.Stream<Path> s = Files.walk(confRoot, 3)) {
                    s.filter(Files::isRegularFile)
                     .filter(p -> !p.getFileName().toString().startsWith("."))
                     .limit(200)
                     .forEach(p -> queue.add(p.toAbsolutePath()));
                }
            } catch (IOException e) {
                warnings.add("扫描配置目录失败: " + e.getMessage());
            }
        }

        // 精确模式：主配置 → include 指令逐层展开（glob 支持 / 符号链接跟随）
        while (!queue.isEmpty()) {
            Path file = queue.poll();
            try {
                Path real = file.toRealPath().normalize();
                if (visited.contains(real.toString())) { continue; }
                visited.add(real.toString());
                if (Files.size(real) > MAX_SCAN_BYTES) { continue; }
                String text = Files.readString(real, StandardCharsets.UTF_8);
                StringBuilder active = new StringBuilder();
                for (String line : text.split("\n", -1)) {
                    if (!line.trim().startsWith("#")) { active.append(line).append('\n'); }
                }
                List<NginxLiteParser.CfgNode> topNodes =
                        NginxLiteParser.parse(active.toString(), real.getFileName().toString());
                NginxLiteParser.CfgNode fileAst = new NginxLiteParser.CfgNode("file-root",
                        List.of(), topNodes, 0, real.getFileName().toString());
                collectUpstreams(fileAst, upByName);
                collectServerBlocks(fileAst, serverBlocks);
                chain.add(real.getFileName().toString());
                files[0]++;
                collectIncludes(fileAst, queue, visited, confRoot, warnings);
            } catch (IOException e) {
                warnings.add("读取配置 " + file + " 失败: " + e.getMessage());
            }
        }

        for (NginxLiteParser.CfgNode sb : serverBlocks) {
            addServer(sb, upByName, backendByKey, servers, backendNodes, edges, warnings);
        }
        for (TopologyGraph.UpstreamNode u : upByName.values()) {
            ups.add(u);
            for (String s : u.getServers()) {
                String beKey = toBackendKey(s);
                if (beKey != null) {
                    ensureBackend(backendByKey, backendNodes, beKey, "upstream:" + u.getName());
                    edges.add(new TopologyGraph.Edge(u.getId(), beKey, "upstream member"));
                }
            }
        }

        g.setServers(servers);
        g.setUpstreams(ups);
        g.setBackends(backendNodes);
        g.setEdges(edges);
        g.setFilesScanned(files[0]);
        g.setChain(chain);
        g.setWarnings(warnings);
        return g;
    }

    private int countIncludes(NginxLiteParser.CfgNode n) {
        int c = 0;
        if (n.children != null) {
            for (NginxLiteParser.CfgNode x : n.children) {
                if ("include".equals(x.name) && x.children == null) { c++; }
                c += countIncludes(x);
            }
        }
        return c;
    }

    /** 收集 include 指令（任意层级），解析 glob/相对路径，入队。 */
    private void collectIncludes(NginxLiteParser.CfgNode node, Deque<Path> queue,
                                 Set<String> visited, Path confRoot, List<String> warnings) {
        if (node.children == null) { return; }
        for (NginxLiteParser.CfgNode c : node.children) {
            if ("include".equals(c.name) && c.children == null && c.arg(0) != null) {
                for (Path p : resolveInclude(c.arg(0), confRoot, warnings)) {
                    Path abs = p.toAbsolutePath();
                    try {
                        Path real = abs.toRealPath().normalize();
                        if (!visited.contains(real.toString())) { queue.add(real); }
                    } catch (IOException ignored) {
                        queue.add(abs);
                    }
                }
            }
            collectIncludes(c, queue, visited, confRoot, warnings);
        }
    }

    /** include 模式解析：支持通配符（glob），相对路径按 conf 根与根父目录候选。 */
    private List<Path> resolveInclude(String pattern, Path confRoot, List<String> warnings) {
        List<Path> out = new ArrayList<>();
        String pat = pattern.replace("\"", "").trim();
        if (pat.startsWith("logs/") || pat.equals("/dev/null") || pat.endsWith(".pid")) { return out; }
        // 拆"目录部分 + 文件名(含通配)"：* 在 Windows 路径里是非法字符，绝不能直接 Paths.get
        int slash = Math.max(pat.lastIndexOf('/'), pat.lastIndexOf('\\'));
        String dirPart = slash > 0 ? pat.substring(0, slash) : "";
        String nameGlob = slash > 0 ? pat.substring(slash + 1) : pat;

        List<Path> dirs = new ArrayList<>();
        if (dirPart.isEmpty()) {
            dirs.add(confRoot);
            if (confRoot.getParent() != null) { dirs.add(confRoot.getParent()); }
        } else {
            boolean dirAbs = dirPart.startsWith("/");
            for (String base : new String[]{confRoot.toString(),
                    confRoot.getParent() == null ? null : confRoot.getParent().toString()}) {
                if (base == null) { continue; }
                if (dirAbs) {
                    // 绝对 include：直接用自身路径（拼接会导致 /etc/nginx//etc/nginx 的双根路径）
                    dirs.add(Paths.get(dirPart));
                    break;
                }
                if (dirPart.contains("*") || dirPart.contains("?")) {
                    // 中间含通配的极端写法不在支持范围（nginx 很少写），跳过
                    continue;
                }
                dirs.add(Paths.get(base, dirPart));
            }
        }

        boolean glob = nameGlob.contains("*") || nameGlob.contains("?");
        for (Path dir : dirs) {
            try {
                if (!Files.isDirectory(dir)) { continue; }
                if (glob) {
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, nameGlob)) {
                        ds.forEach(out::add);
                    }
                } else {
                    Path f = dir.resolve(nameGlob);
                    if (Files.isRegularFile(f)) { out.add(f); }
                }
            } catch (IOException | java.nio.file.InvalidPathException e) {
                warnings.add("include 解析失败 " + pat + ": " + e.getMessage());
            }
        }
        return out;
    }

    private void collectUpstreams(NginxLiteParser.CfgNode fileAst,
                                  Map<String, TopologyGraph.UpstreamNode> out) {
        List<NginxLiteParser.CfgNode> blocks =
                fileAst.findBlocks("upstream");
        for (NginxLiteParser.CfgNode up : blocks) {
            String name = up.arg(0);
            if (name == null) { continue; }
            TopologyGraph.UpstreamNode u = new TopologyGraph.UpstreamNode();
            u.setId("U:" + name);
            u.setName(name);
            u.setFile(up.file);
            List<String> members = new ArrayList<>();
            if (up.children != null) {
                for (NginxLiteParser.CfgNode c : up.children) {
                    if ("server".equals(c.name) && c.children == null && c.arg(0) != null) {
                        members.add(c.arg(0) + (c.arg(1) != null ? " " + c.arg(1) : ""));
                    }
                    if ("least_conn".equals(c.name)) { u.setHeaderInput("least_conn"); }
                    if ("ip_hash".equals(c.name)) { u.setHeaderInput("ip_hash"); }
                }
            }
            u.setServers(members);
            out.putIfAbsent(name, u);
        }
    }

    private void collectServerBlocks(NginxLiteParser.CfgNode fileAst,
                                     List<NginxLiteParser.CfgNode> out) {
        // http 块内的 server，或文件顶层 server（sites-enabled 风格：文件根就是 server 块）
        collectServers(fileAst, out);
    }

    private void collectServers(NginxLiteParser.CfgNode n, List<NginxLiteParser.CfgNode> out) {
        if ("server".equals(n.name) && n.children != null) { out.add(n); }
        if (n.children != null) {
            for (NginxLiteParser.CfgNode c : n.children) {
                if ("upstream".equals(c.name)) { continue; }
                collectServers(c, out);
            }
        }
    }

    private void addServer(NginxLiteParser.CfgNode s, Map<String, TopologyGraph.UpstreamNode> upsById,
                           Map<String, TopologyGraph.BackendNode> backendByKey,
                           List<TopologyGraph.ServerNode> servers,
                           List<TopologyGraph.BackendNode> backendNodes,
                           List<TopologyGraph.Edge> edges, List<String> warnings) {
        TopologyGraph.ServerNode node = new TopologyGraph.ServerNode();
        node.setId(servers.size() == 0 ? "S:0" : "S:" + servers.size());
        String serverId = node.getId();
        node.setFile(s.file);

        List<String> listen = new ArrayList<>();
        List<String> domains = new ArrayList<>();
        boolean ssl = false;
        for (NginxLiteParser.CfgNode c : s.direct("listen")) {
            if (c.arg(0) != null) {
                StringBuilder lb = new StringBuilder(c.arg(0));
                for (int k = 1; k < c.args.size(); k++) {
                    if ("ssl".equalsIgnoreCase(c.args.get(k))) { ssl = true; }
                    if ("default_server".equalsIgnoreCase(c.args.get(k))) { lb.append("(default)"); }
                }
                listen.add(lb.toString());
            }
        }
        for (NginxLiteParser.CfgNode c : s.direct("server_name")) {
            if (c.arg(0) != null) {
                for (String d : c.args) { if (d != null) { domains.add(d); } }
            }
        }
        node.setListen(listen);
        node.setDomains(domains.size() > 0 ? domains : List.of("_"));
        node.setSsl(ssl);
        node.setDisplayName(serverId + " " + String.join(", ", listen) + (ssl ? " (SSL)" : ""));
        node.setLocations(new ArrayList<>());

        // locations & server-level passes
        for (NginxLiteParser.CfgNode locNode : s.direct("location")) {
            String locPath = locNode.arg(locNode.args.size() >= 2 ? 1 : 0);
            if (locPath == null) { locPath = "/"; }
            resolveServerLocal(locNode, locPath, serverId, node.getLocations(), edges, warnings, upsById,
                    backendByKey, backendNodes);
        }
        // server 级直连（如整个 server 直接 proxy_pass，无 location）
        resolveServerLocal(s, "/", serverId, node.getLocations(), edges, warnings, upsById,
                backendByKey, backendNodes);

        servers.add(node);
        // 客户端链路
        edges.add(new TopologyGraph.Edge("C:web", serverId,
                String.join(", ", listen) + (ssl ? " SSL" : "")));
    }

    private void resolveServerLocal(NginxLiteParser.CfgNode scope, String path, String serverId,
                                    List<TopologyGraph.LocationInfo> locations,
                                    List<TopologyGraph.Edge> edges, List<String> warnings,
                                    Map<String, TopologyGraph.UpstreamNode> upByName,
                                    Map<String, TopologyGraph.BackendNode> backendByKey,
                                    List<TopologyGraph.BackendNode> backendNodes) {
        // 取第一个用于 代理 的 pass
        String[] passDirectives = {"proxy_pass", "grpc_pass", "fastcgi_pass", "uwsgi_pass", "memcached_pass"};
        NginxLiteParser.CfgNode passNode = null;
        String passName = null;
        outer:
        for (String pd : passDirectives) {
            for (NginxLiteParser.CfgNode c : scope.direct(pd)) {
                if (c.children == null && c.arg(0) != null) {
                    passNode = c; passName = pd; break outer;
                }
            }
        }
        if (passNode != null) {
            String target = passNode.arg(0).replaceAll(";$", "");
            TopologyGraph.LocationInfo li = new TopologyGraph.LocationInfo();
            li.setPath(path);
            List<String> nextIds = new ArrayList<>();
            if ("proxy_pass".equals(passName) || "grpc_pass".equals(passName)) {
                String trimmed = target.startsWith("http://") ? target.substring(7)
                        : target.startsWith("https://") ? target.substring(8) : target;
                trimmed = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
                boolean https = target.startsWith("https://");
                TopologyGraph.UpstreamNode up = upByName.get(trimmed);
                if (up != null) {
                    li.setType("proxy");
                    li.setRouteTarget("upstream:" + trimmed);
                    nextIds.add("U:" + trimmed);
                    edges.add(new TopologyGraph.Edge(serverId, up.getId(),
                            path + " " + passName + " → " + trimmed));
                } else {
                    li.setType("direct");
                    String beKey = (https ? "https://" : "http://") + trimmed;
                    li.setRouteTarget(beKey);
                    ensureBackend(backendByKey, backendNodes, beKey, "server:" + serverId);
                    nextIds.add(beKey);
                    edges.add(new TopologyGraph.Edge(serverId, beKey, path + " → " + trimmed));
                }
            } else {
                li.setType("direct");
                li.setRouteTarget(target);
                ensureBackend(backendByKey, backendNodes, target, "server:" + serverId);
                nextIds.add(target);
                edges.add(new TopologyGraph.Edge(serverId, target, path + " → " + passName));
            }
            li.setNodeIds(nextIds);
            locations.add(li);
            return;
        }
        String root = scope.firstArg("root", 0) != null ? scope.firstArg("root", 0)
                : scope.firstArg("alias", 0);
        if (root != null) {
            TopologyGraph.LocationInfo li = new TopologyGraph.LocationInfo();
            li.setPath(path);
            li.setType("static");
            li.setRouteTarget(root);
            li.setNodeIds(List.of("T:" + root));
            locations.add(li);
            edges.add(new TopologyGraph.Edge(serverId, "T:" + root, path + " static root"));
        }
    }

    private String toBackendKey(String srv) {
        // upstream 成员格式：host:port [参数]；unix:/path…
        String host = srv.trim().split("\\s+")[0];
        if (host.startsWith("unix:")) { return "unix:" + host.substring(4); }
        if (host.contains(":")) { return "http://" + host; }                       // 已含端口
        return "http://" + host + ":80";                                            // 省略端口默认 80
    }

    private void ensureBackend(Map<String, TopologyGraph.BackendNode> map,
                               List<TopologyGraph.BackendNode> nodes, String key, String from) {
        if (!map.containsKey(key)) {
            TopologyGraph.BackendNode b = new TopologyGraph.BackendNode();
            b.setId(key);
            b.setScheme(key.startsWith("unix") ? "unix" : (key.startsWith("https") ? "https" : "http"));
            b.setFrom(from);
            map.put(key, b);
            nodes.add(b);
        }
    }
}
