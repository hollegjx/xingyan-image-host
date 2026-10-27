package learn.gjx.tuku1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import learn.gjx.tuku1.model.entity.Space;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SpaceMapper extends BaseMapper<Space> {
    /** 仅在事务内调用；额度更新和删除使用同一行锁保护。 */
    @Select("SELECT * FROM space WHERE id = #{id} AND isDelete = 0 FOR UPDATE")
    Space selectForUpdate(@Param("id") Long id);

    /** 第一组仅允许删除完全无图片引用的空间，连逻辑删除记录也保守阻止。 */
    @Select("SELECT COUNT(*) FROM picture WHERE spaceId = #{id}")
    long countPictureReferences(@Param("id") Long id);
}
