package yi.shi.plinth.ngx.dto;

import lombok.Data;

import java.util.List;

/**
 * 网络拓扑图数据（配置解析所得的节点 + 边）。
 * id 前缀约定：S=server 块，U=upstream，B=backend，T=static 根目录，C=client。
 */
@Data
public class TopologyGraph {

    private List<ServerNode> servers;
    private List<UpstreamNode> upstreams;
    private List<BackendNode> backends;
    private List<Edge> edges;
    /** 扫描的配置文件数与解析告警（未找到同名 upstream 等） */
    private int filesScanned;
    /** include 展开链（入口主配置 + 按序命中的 include 文件名） */
    private List<String> chain;
    /** true=从 nginx.conf 起 include 链精确读取；false=目录全扫兜底 */
    private boolean precise = true;
    private List<String> warnings;

    @Data
    public static class ServerNode {
        private String id;
        private String file;
        private List<String> listen;
        private List<String> domains;
        private boolean ssl;
        private String displayName;
        private List<LocationInfo> locations;
    }

    @Data
    public static class LocationInfo {
        private String path;
        /** proxy（upstream）/direct（直连后端）/static/return/other */
        private String type;
        private String routeTarget; // upstream 名 / backend key / root 目录 / return 说明
        private List<String> nodeIds; // 该 location 指向的下一跳节点 id
    }

    @Data
    public static class UpstreamNode {
        private String id;
        private String name;
        private String file;
        private List<String> servers;
        private String headerInput; // keepalive/least_conn 等策略说明（首参）
    }

    @Data
    public static class BackendNode {
        private String id; // host:port 或 unix:/path
        private String scheme; // http / https / unix
        private String from; // 直接 proxy_pass 所在 server/file 描述
    }

    @Data
    public static class Edge {
        private String from;
        private String to;
        private String label;
        public Edge() { }
        public Edge(String from, String to, String label) {
            this.from = from; this.to = to; this.label = label;
        }
    }
}
