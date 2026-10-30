package learn.gjx.tuku1.mapper;
import learn.gjx.tuku1.model.entity.Picture;
import org.apache.ibatis.annotations.*;
import java.util.List;
public interface PictureFavoriteMapper {
    String VISIBLE = " FROM picture_favorite f JOIN picture p ON p.id=f.pictureId WHERE f.userId=#{userId} AND p.isDelete=0 AND p.spaceId IS NULL AND p.reviewStatus=1 AND p.uploadStatus=1";
    @Select("SELECT p.*" + VISIBLE + " ORDER BY f.createTime DESC, f.pictureId DESC LIMIT #{size} OFFSET #{offset}")
    List<Picture> mine(@Param("userId") long userId, @Param("size") int size, @Param("offset") long offset);
    @Select("SELECT COUNT(*)" + VISIBLE)
    long total(@Param("userId") long userId);
}
