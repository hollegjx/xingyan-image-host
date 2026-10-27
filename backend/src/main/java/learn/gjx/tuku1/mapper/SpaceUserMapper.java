package learn.gjx.tuku1.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import learn.gjx.tuku1.model.entity.SpaceUser;
import org.apache.ibatis.annotations.*;

public interface SpaceUserMapper extends BaseMapper<SpaceUser> {
    @Select("SELECT * FROM space_user WHERE spaceId=#{spaceId} AND userId=#{userId}")
    SpaceUser findMember(@Param("spaceId") Long spaceId, @Param("userId") Long userId);

    /** Use after locking the space row: current read, not an earlier repeatable-read snapshot. */
    @Select("SELECT * FROM space_user WHERE spaceId=#{spaceId} AND userId=#{userId} FOR UPDATE")
    SpaceUser lockMember(@Param("spaceId") Long spaceId, @Param("userId") Long userId);
}
