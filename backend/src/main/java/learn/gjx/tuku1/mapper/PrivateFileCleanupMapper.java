package learn.gjx.tuku1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import learn.gjx.tuku1.model.entity.PrivateFileCleanup;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface PrivateFileCleanupMapper extends BaseMapper<PrivateFileCleanup> {
    @Select("SELECT * FROM private_file_cleanup WHERE id=#{id} FOR UPDATE")
    PrivateFileCleanup lock(@Param("id") Long id);

    @Select("SELECT * FROM private_file_cleanup WHERE spaceId=#{spaceId} AND url=#{url} ORDER BY id FOR UPDATE")
    List<PrivateFileCleanup> lockByUrl(@Param("spaceId") Long spaceId, @Param("url") String url);

    @Select("SELECT COUNT(*) FROM picture WHERE isDelete=0 AND (url=#{url} OR originalUrl=#{url} OR thumbnailUrl=#{url})")
    long liveReferences(@Param("url") String url);
}
