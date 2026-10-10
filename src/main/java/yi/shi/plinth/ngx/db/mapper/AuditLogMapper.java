package yi.shi.plinth.ngx.db.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import yi.shi.plinth.ngx.db.entity.AuditLog;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO ngx_audit_log (username, action, file_path, detail, result, client_ip, create_time) "
            + "VALUES (#{username}, #{action}, #{filePath}, #{detail}, #{result}, #{clientIp}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog log);

    @Select("SELECT * FROM ngx_audit_log ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<AuditLog> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ngx_audit_log")
    long count();

    @Select("SELECT * FROM ngx_audit_log WHERE action = #{action} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<AuditLog> findPageByAction(@Param("action") String action, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ngx_audit_log WHERE action = #{action}")
    long countByAction(@Param("action") String action);

    @Select("SELECT * FROM ngx_audit_log ORDER BY id DESC LIMIT #{limit}")
    List<AuditLog> findRecent(@Param("limit") int limit);
}
