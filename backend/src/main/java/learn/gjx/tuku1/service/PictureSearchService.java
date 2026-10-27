package learn.gjx.tuku1.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import learn.gjx.tuku1.api.imagesearch.*;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.config.ImageSearchProperties;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.model.dto.picture.SearchPictureByPictureRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class PictureSearchService {
    private final PictureMapper pictures;
    private final PictureAccessManager access;
    private final ImageSearchApiFacade search;
    private final ImageSearchProperties settings;
    private final CosClientConfig cos;
    private final Semaphore inFlight = new Semaphore(2);
    private final Cache<Long, Boolean> cooldown = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterWrite(10, TimeUnit.SECONDS).build();

    public PictureSearchService(PictureMapper pictures, PictureAccessManager access,
                                ImageSearchApiFacade search, ImageSearchProperties settings, CosClientConfig cos) {
        this.pictures = pictures;
        this.access = access;
        this.search = search;
        this.settings = settings;
        this.cos = cos;
    }

    public List<ImageSearchResult> search(SearchPictureByPictureRequest request, User caller) {
        ThrowUtils.throwIf(caller == null || caller.getId() == null || caller.getId() <= 0,
                ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request == null || request.getPictureId() == null || request.getPictureId() <= 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        Picture picture = pictures.selectById(request.getPictureId());
        ThrowUtils.throwIf(picture == null || Integer.valueOf(1).equals(picture.getIsDelete()),
                ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        access.checkRead(picture, caller);
        ThrowUtils.throwIf(!access.isPubliclyVisible(picture), ErrorCode.NO_AUTH_ERROR,
                "仅支持已审核通过的公开图片；私人图片、草稿和待审核图片不会发送给第三方");
        ThrowUtils.throwIf(!Boolean.TRUE.equals(request.getExternalSearchConsent()), ErrorCode.PARAMS_ERROR,
                "请先确认将此公开图片地址发送给百度识图");
        ThrowUtils.throwIf(!settings.isEnabled(), ErrorCode.SERVICE_UNAVAILABLE,
                "以图搜图尚未开放，第三方服务验收完成后启用");
        String source = picture.getOriginalUrl();
        if (source == null || source.trim().isEmpty()) source = picture.getUrl();
        try {
            if (cos.getBucket() == null || !cos.getBucket().matches("[a-z0-9-]+-[0-9]+")
                    || cos.getRegion() == null || !cos.getRegion().matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException("Storage not configured");
            }
            source = ImageSearchUrls.source(source, cos.getBucket() + ".cos." + cos.getRegion() + ".myqcloud.com");
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持本站公共 COS 桶中的无签名图片地址");
        }
        ThrowUtils.throwIf(cooldown.asMap().putIfAbsent(caller.getId(), Boolean.TRUE) != null,
                ErrorCode.TOO_MANY_REQUEST, "请等待 10 秒后再次搜索");
        ThrowUtils.throwIf(!inFlight.tryAcquire(), ErrorCode.TOO_MANY_REQUEST, "图片搜索繁忙，请稍后再试");
        try { return search.search(source); }
        finally { inFlight.release(); }
    }
}
