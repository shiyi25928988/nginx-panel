package yi.shi.plinth.ngx.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 证书清单条目（由配置 ssl_certificate 指令发现）。
 */
@Data
public class CertView {
    /** 证书文件绝对路径 */
    private String certPath;
    /** 配对私钥路径（未声明则为 null） */
    private String keyPath;
    private List<String> domains;
    private String subject;
    private String issuer;
    private LocalDateTime notBefore;
    private LocalDateTime notAfter;
    /** 剩余天数（负数=已过期）；证书缺失/解析失败为 null */
    private Long daysLeft;
    private boolean expired;
    /** 证书文件是否存在可读 */
    private boolean certExists;
    /** true=位于面板证书目录（cert.dir，可被面板替换/自签覆盖）；false=系统/外部路径（只展示，不可面板替换） */
    private boolean managed;
    /** true=已被配置（ssl_certificate 指令）引用；false=在证书目录中但尚未被引用 */
    private boolean inUse;
    /** 证书与私钥是否匹配；null=未知（私钥不可读/未声明） */
    private Boolean keyMatch;
    private String keyAlg;
    private long size;
    private Long lastModified;
}
