package yi.shi.plinth.ngx.db.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yi.shi.plinth.ngx.db.entity.ConfigVersion;

import java.util.List;

@Mapper
public interface ConfigVersionMapper {

    @Insert("INSERT INTO ngx_config_version (file_path, old_content, new_content, md5, version_no, editor, reason, create_time) "
            + "VALUES (#{filePath}, #{oldContent}, #{newContent}, #{md5}, #{versionNo}, #{editor}, #{reason}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ConfigVersion version);

    @Select("SELECT * FROM ngx_config_version WHERE file_path = #{filePath} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<ConfigVersion> findPageByPath(@Param("filePath") String filePath, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ngx_config_version WHERE file_path = #{filePath}")
    long countByPath(@Param("filePath") String filePath);

    @Select("SELECT * FROM ngx_config_version WHERE id = #{id}")
    ConfigVersion findById(@Param("id") Long id);

    @Select("SELECT IFNULL(MAX(version_no), 0) FROM ngx_config_version WHERE file_path = #{filePath}")
    int maxVersionNo(@Param("filePath") String filePath);

    /** 最近改动（跨文件，控制台概览用） */
    @Select("SELECT id, file_path, version_no, editor, reason, md5, create_time FROM ngx_config_version "
            + "ORDER BY id DESC LIMIT #{limit}")
    List<ConfigVersion> findRecent(@Param("limit") int limit);
}
