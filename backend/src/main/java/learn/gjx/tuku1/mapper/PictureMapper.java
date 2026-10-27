package learn.gjx.tuku1.mapper;

import learn.gjx.tuku1.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author GJX
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2025-12-17 22:42:43
* @Entity generator.domain.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM picture WHERE id=#{id} AND isDelete=0 FOR UPDATE")
    Picture selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

}




