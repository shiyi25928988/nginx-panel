package yi.shi.plinth.ngx.service;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.user.CurrentUser;
import yi.shi.plinth.ngx.db.entity.ConfigVersion;
import yi.shi.plinth.ngx.db.mapper.ConfigVersionMapper;
import yi.shi.plinth.ngx.dto.FileContent;
import yi.shi.plinth.ngx.dto.PageData;
import yi.shi.plinth.ngx.NginxPanelException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 配置版本服务：记录每次保存的前后内容，支持按文件追溯与回滚取值。
 */
@Slf4j
@Singleton
public class VersionService {

    @Inject
    private ConfigVersionMapper versionMapper;

    @Inject
    private CurrentUser currentUser;

    /** 记录一次成功保存（oldContent 为 null 表示新建文件）。 */
    public long record(String filePath, String oldContent, String newContent, String reason) {
        ConfigVersion version = new ConfigVersion();
        version.setFilePath(filePath);
        version.setOldContent(oldContent);
        version.setNewContent(newContent);
        // 删除文件动作记 null 内容，md5 以常量标记
        version.setMd5(newContent == null ? "DELETED" : md5Hex(newContent));
        version.setVersionNo(versionMapper.maxVersionNo(filePath) + 1);
        version.setEditor(currentUsername());
        version.setReason(reason);
        version.setCreateTime(LocalDateTime.now());
        versionMapper.insert(version);
        return version.getId();
    }

    /** 分页查询某文件的版本列表（仅元数据；hasPrev 标记撤销可行性，完整内容走 /api/history/version）。 */
    public PageData<ConfigVersion> listByPath(String filePath, int page, int size) {
        List<ConfigVersion> versions = versionMapper.findPageByPath(filePath, (page - 1) * size, size);
        versions.forEach(v -> {
            v.setHasPrev(v.getOldContent() != null);
            v.setNewContent(null);
            v.setOldContent(null);
        });
        return new PageData<>(versions, versionMapper.countByPath(filePath), page, size);
    }

    /** 取版本元数据（不含内容）。 */
    public ConfigVersion getMeta(long id) {
        ConfigVersion version = versionMapper.findById(id);
        if (version != null) {
            version.setNewContent(null);
            version.setOldContent(null);
        }
        return version;
    }

    /** 取版本完整记录（内容字段保留，供回滚流程判定）。 */
    public ConfigVersion getFull(long id) {
        return versionMapper.findById(id);
    }

    /** 取版本完整内容（回滚用）。 */
    public String contentOf(long id) {
        ConfigVersion version = versionMapper.findById(id);
        if (version == null) {
            throw new NginxPanelException("版本不存在: " + id);
        }
        return version.getNewContent();
    }

    /** 取版本的修改前内容（新建文件为 null）。 */
    public String prevOf(long id) {
        ConfigVersion version = versionMapper.findById(id);
        return version == null ? null : version.getOldContent();
    }

    /** 最近改动（控制台概览）。 */
    public List<ConfigVersion> recent(int limit) {
        return versionMapper.findRecent(limit);
    }

    private String currentUsername() {
        try {
            return StpUtil.getLoginIdDefaultNull() == null ? "system" : currentUser.username();
        } catch (Exception e) {
            return "system";
        }
    }

    /** 标准小写 hex MD5（32 位，保留前导零）。 */
    private static String md5Hex(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new NginxPanelException("MD5 计算失败", e);
        }
    }
}
