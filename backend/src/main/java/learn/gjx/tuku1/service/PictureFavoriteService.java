package learn.gjx.tuku1.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Service @Slf4j
public class PictureFavoriteService {
    @Resource private JdbcTemplate jdbc;
    @Resource(name = "pictureMapper") private PictureMapper pictures;
    @Resource private PictureFavoriteMapper favorites;
    @Resource private PictureAccessManager access;
    @Resource private PictureService pictureService;
    @Resource private UserService users;

    /** 图片行锁串行化同图的收藏变更；唯一约束兜底，不维护可漂移的冗余计数。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public PictureFavoriteVO set(PictureFavoriteRequest r, User actor) {
        PictureAccessManager.requireActiveUser(actor);
        if (r == null || r.getPictureId() == null || r.getPictureId() <= 0 || r.getFavorite() == null)
            throw failure(ErrorCode.PARAMS_ERROR, "收藏参数不合法");
        Picture picture = pictures.selectForUpdate(r.getPictureId());
        boolean visible = picture != null && access.isPubliclyVisible(picture);
        if (r.getFavorite()) {
            if (!visible) throw failure(ErrorCode.NOT_FOUND_ERROR, "图片不存在或不可收藏");
            if (!contains(actor.getId(), picture.getId()))
                jdbc.update("INSERT INTO picture_favorite(userId,pictureId) VALUES(?,?)", actor.getId(), picture.getId());
        } else {
            // 下架或删除后仍能移除自己的收藏，不返回该图片的私有信息。
            jdbc.update("DELETE FROM picture_favorite WHERE userId=? AND pictureId=?", actor.getId(), r.getPictureId());
        }
        return new PictureFavoriteVO(visible ? count(r.getPictureId()) : 0, r.getFavorite());
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, PictureFavoriteStatusVO> status(PictureFavoriteStatusRequest r, HttpServletRequest request) {
        if (r == null || r.getPictureIds() == null || r.getPictureIds().isEmpty() || r.getPictureIds().size() > 50
                || r.getPictureIds().stream().anyMatch(id -> id == null || id <= 0))
            throw failure(ErrorCode.PARAMS_ERROR, "每次请查询1至50张图片");
        User actor = null;
        if (request.getSession(false) != null) {
            try { actor = users.getLoginUser(request); }
            catch (BusinessException e) {
                if (e.getCode() != ErrorCode.NOT_LOGIN_ERROR.getCode() && e.getCode() != ErrorCode.NOT_FOUND_ERROR.getCode()) throw e;
            }
        }
        final boolean authenticated = actor != null;
        java.util.List<Long> ids = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(r.getPictureIds()));
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(authenticated ? actor.getId() : -1L);
        args.addAll(ids);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT p.id, COUNT(f.userId) AS favoriteCount, MAX(CASE WHEN f.userId=? THEN 1 ELSE 0 END) AS favorited "
                + "FROM picture p LEFT JOIN picture_favorite f ON f.pictureId=p.id "
                + "WHERE p.isDelete=0 AND p.spaceId IS NULL AND p.reviewStatus=1 AND p.uploadStatus=1 "
                + "AND p.id IN (" + placeholders + ") GROUP BY p.id";
        java.util.Map<String, PictureFavoriteStatusVO> result = new java.util.LinkedHashMap<>();
        jdbc.query(sql, args.toArray(), (org.springframework.jdbc.core.RowCallbackHandler) row ->
                result.put(Long.toString(row.getLong("id")), new PictureFavoriteStatusVO(
                        row.getLong("favoriteCount"), authenticated ? row.getInt("favorited") == 1 : null)));
        return result;
    }

    public long count(long pictureId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM picture_favorite WHERE pictureId=?", Long.class, pictureId);
    }
    public boolean contains(long userId, long pictureId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM picture_favorite WHERE userId=? AND pictureId=?", Long.class, userId, pictureId) > 0;
    }
    public void decorate(PictureDetailVO detail, Picture picture, HttpServletRequest request) {
        boolean visible = access.isPubliclyVisible(picture);
        detail.setFavoriteCount(visible ? count(picture.getId()) : 0);
        if (request.getSession(false) == null) return;
        User actor;
        try { actor = users.getLoginUser(request); }
        catch (BusinessException e) {
            if (e.getCode() == ErrorCode.NOT_LOGIN_ERROR.getCode() || e.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()) return;
            throw e;
        }
        detail.setFavorited(visible && contains(actor.getId(), picture.getId()));
    }
    @Transactional(readOnly = true)
    public Page<PictureVO> mine(FavoritePageRequest r, User actor, HttpServletRequest request) {
        PictureAccessManager.requireActiveUser(actor);
        if (r == null || r.getCurrent() == null || r.getCurrent() < 1 || r.getPageSize() == null || r.getPageSize() < 1 || r.getPageSize() > 50)
            throw failure(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        Page<PictureVO> result = new Page<>(r.getCurrent(), r.getPageSize(), favorites.total(actor.getId()));
        result.setRecords(pictureService.getPictureVOList(favorites.mine(actor.getId(), r.getPageSize(), ((long)r.getCurrent()-1)*r.getPageSize()), request));
        return result;
    }
    private BusinessException failure(ErrorCode code, String message) {
        log.warn("收藏请求被拒绝：{}", message); return new BusinessException(code, message);
    }
}
