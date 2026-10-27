package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.config.PrivatePictureStorageProperties;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.entity.Picture;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/** Streams authorized objects; never redirects or discloses a reusable COS URL to a client. */
@Component
public class PrivatePictureFileManager {
    @Resource
    private COSClient cosClient;
    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private PrivatePictureStorageProperties properties;

    public void write(Picture picture, String variant, HttpServletResponse response) throws IOException {
        String storedUrl;
        switch (variant) {
            case "display": storedUrl = picture.getUrl(); break;
            case "original": storedUrl = picture.getOriginalUrl(); break;
            case "thumbnail": storedUrl = picture.getThumbnailUrl(); break;
            default: throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的图片类型");
        }
        String key = validatedKey(picture.getSpaceId(), storedUrl);
        // Authentication/ownership is checked by PictureService before this method is called.
        // No public-bucket fallback, including on configuration or COS failures.
        COSObject object;
        try {
            object = cosClient.getObject(new GetObjectRequest(properties.getBucket(), key));
        } catch (RuntimeException error) {
            // SDK exception messages can contain internal addresses; do not return them to the client.
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "私有图片读取失败");
        }
        try (COSObject ignored = object) {
            secureHeaders(response);
            response.setHeader("Content-Disposition", "inline");
            String extension = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            response.setContentType("image/" + ("jpg".equals(extension) ? "jpeg" : extension));
            StreamUtils.copy(object.getObjectContent(), response.getOutputStream());
        } catch (IOException | RuntimeException error) {
            if (response.isCommitted()) {
                // A partially sent image cannot turn into a JSON error or a public-file redirect.
                throw new IOException("Private image transfer interrupted");
            }
            response.reset();
            secureHeaders(response);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "私有图片读取失败");
        }
    }

    private void secureHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Vary", "Cookie");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    public void delete(Long spaceId, String storedUrl) {
        String key = validatedKey(spaceId, storedUrl);
        try {
            cosClient.deleteObject(properties.getBucket(), key);
        } catch (com.qcloud.cos.exception.CosServiceException error) {
            // 重试可能遇到已成功删除但上次响应丢失；只吞掉明确的对象不存在。
            if (error.getStatusCode() != 404 || !"NoSuchKey".equals(error.getErrorCode())) { throw error; }
        }
    }

    public boolean isEnabled() { return properties.isEnabled(); }

    public String bucket(Long spaceId) {
        // 与读取相同的配置、路径约束，避免上传端使用另一套安全边界。
        validatedKey(spaceId, url("space/" + spaceId + "/probe.jpg"));
        return properties.getBucket();
    }

    public String url(String key) {
        return "https://" + properties.getBucket() + ".cos." + cosClientConfig.getRegion()
                + ".myqcloud.com/" + key;
    }

    public String validatedKey(Long spaceId, String storedUrl) {
        String bucket = properties.getBucket();
        String region = cosClientConfig.getRegion();
        if (!properties.isEnabled() || bucket == null || !bucket.matches("[a-z0-9-]+-[0-9]+")
                || bucket.equals(cosClientConfig.getBucket())
                || region == null || !region.matches("[a-z0-9-]+")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "私有图片存储尚未配置");
        }
        if (spaceId == null || spaceId <= 0 || storedUrl == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "私有图片文件不存在");
        }
        try {
            URI uri = URI.create(storedUrl);
            String host = bucket + ".cos." + region + ".myqcloud.com";
            String path = uri.getRawPath();
            if (!"https".equals(uri.getScheme()) || !host.equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || path == null || !path.matches("/space/" + spaceId
                    + "/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp|gif)")) {
                throw new IllegalArgumentException("Invalid private object reference");
            }
            return path.substring(1);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "私有图片存储路径不合法");
        }
    }
}
