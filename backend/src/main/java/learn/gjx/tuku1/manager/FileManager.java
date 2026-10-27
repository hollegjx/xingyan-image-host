package learn.gjx.tuku1.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRule;
import learn.gjx.tuku1.model.vo.PictureProcessInfoVO;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 文件管理器：在 {@link CosManager} 之上补充校验、路径生成以及数据万象相关能力。
 */
@Component
@Slf4j
public class FileManager {

    private static final long MAX_PICTURE_SIZE = 2 * 1024 * 1024L;

    private static final int MAX_REDIRECT_COUNT = 3;

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private static final int READ_TIMEOUT_MS = 10000;

    private static final List<String> ALLOWED_PICTURE_SUFFIXES = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

    @Value("${picture.derivative.enabled:true}")
    private boolean derivativeEnabled = true;

    @Value("${picture.derivative.display-quality:85}")
    private int displayQuality = 85;

    @Value("${picture.derivative.thumbnail-quality:80}")
    private int thumbnailQuality = 80;

    @Value("${picture.derivative.thumbnail-max-width:512}")
    private int thumbnailMaxWidth = 512;

    @Value("${picture.derivative.thumbnail-max-height:512}")
    private int thumbnailMaxHeight = 512;

    @Value("${picture.derivative.thumbnail-min-bytes:20480}")
    private long thumbnailMinBytes = 20 * 1024L;

    @Resource
    private CosManager cosManager;

    @Resource
    private PrivatePictureFileManager privateStorage;

    @Resource
    private PrivatePictureCleanupManager privateCleanup;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 上传图片：完成校验、生成 COS Key，并解析数据万象返回的元信息。
     *
     * @param multipartFile    前端上传的二进制文件
     * @param uploadPathPrefix 例如 {@code public/{userId}}
     * @return 包含图片尺寸、大小等信息的结果
     */
    public PictureUploadResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        return uploadPicture(multipartFile, uploadPathPrefix, null);
    }

    public PictureUploadResult uploadPrivatePicture(MultipartFile multipartFile, Long spaceId) {
        privateStorage.bucket(spaceId);
        return uploadPicture(multipartFile, "space/" + spaceId, spaceId);
    }

    private PictureUploadResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix, Long spaceId) {
        // 基础校验：非空、后缀、大小
        validatePicture(multipartFile);

        // 原始文件名用于展示
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);

        File tempFile = null;
        try {
            // COS SDK 需要先写入临时文件
            tempFile = File.createTempFile("upload_", "." + suffix);
            multipartFile.transferTo(tempFile);
            return uploadLocalPicture(tempFile, originalFilename, tempFile.length(), uploadPathPrefix, spaceId);
        } catch (IOException e) {
            log.error("上传图片失败, originalFilename: {}", originalFilename, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 通过远程 URL 导入图片，并上传到 COS。
     */
    public PictureUploadResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
        return uploadPictureByUrl(fileUrl, uploadPathPrefix, null);
    }

    public PictureUploadResult uploadPrivatePictureByUrl(String fileUrl, Long spaceId) {
        privateStorage.bucket(spaceId);
        return uploadPictureByUrl(fileUrl, "space/" + spaceId, spaceId);
    }

    private PictureUploadResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix, Long spaceId) {
        URL url = validateRemotePictureUrl(fileUrl);
        RemotePictureFile remotePictureFile = downloadRemotePicture(url);
        File tempFile = remotePictureFile.getFile();
        try {
            return uploadLocalPicture(tempFile, remotePictureFile.getOriginalFilename(),
                    remotePictureFile.getFileSize(), uploadPathPrefix, spaceId);
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("远程图片临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private PictureUploadResult uploadLocalPicture(File file, String originalFilename, long fileSize, String uploadPathPrefix,
                                                   Long spaceId) {
        String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(originalFilename), "").toLowerCase(Locale.ROOT);
        validatePictureSize(fileSize);
        validatePictureSuffix(suffix);
        validatePictureSignature(file, suffix);

        String uploadFilename = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()),
                RandomUtil.randomString(16),
                suffix);
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);

        String bucket = spaceId == null ? cosClientConfig.getBucket() : privateStorage.bucket(spaceId);
        DerivativePlan derivativePlan = buildDerivativePlan(uploadPath, suffix, fileSize, bucket);
        List<String> plannedUrls = Arrays.asList(uploadPath, derivativePlan.getDisplayKey(), derivativePlan.getThumbnailKey())
                .stream().filter(StrUtil::isNotBlank).map(key -> targetUrl(key, spaceId)).collect(Collectors.toList());
        if (spaceId != null) {
            privateCleanup.registerUpload(spaceId, plannedUrls);
        }
        try {
            PutObjectResult putObjectResult = spaceId == null
                    ? cosManager.putPictureObject(uploadPath, file, derivativePlan.getPicOperations())
                    : cosManager.putPrivatePictureObject(bucket, uploadPath, file, derivativePlan.getPicOperations());
            return buildUploadResult(putObjectResult, derivativePlan, originalFilename, fileSize, spaceId);
        } catch (RuntimeException e) {
            if (spaceId != null) {
                privateCleanup.abandon(spaceId, plannedUrls);
                log.warn("私有图片上传失败，已登记补偿，spaceId={}", spaceId);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "私有图片上传失败");
            }
            // 数据万象处理异常时，原图可能已经写入 COS。清理所有已知 Key，避免产生孤儿文件。
            cleanupObjectQuietly(uploadPath, "upload-failed");
            cleanupObjectQuietly(derivativePlan.getDisplayKey(), "upload-failed");
            cleanupObjectQuietly(derivativePlan.getThumbnailKey(), "upload-failed");
            throw e;
        }
    }

    private DerivativePlan buildDerivativePlan(String originalKey, String suffix, long fileSize, String bucket) {
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();
        String normalizedSuffix = StrUtil.blankToDefault(suffix, "").toLowerCase(Locale.ROOT);
        String baseKey = removeFileSuffix(originalKey);
        String displayKey = null;
        String thumbnailKey = null;

        // GIF 转 WebP 是否保留动画依赖云端处理能力，未验证前保留原始 GIF，避免静态化。
        if (derivativeEnabled && !"gif".equals(normalizedSuffix)) {
            if (!"webp".equals(normalizedSuffix)) {
                displayKey = baseKey + "_display.webp";
                rules.add(buildDerivativeRule(bucket, displayKey,
                        String.format("imageMogr2/format/webp/quality/%d", displayQuality)));
            }
            if (fileSize > thumbnailMinBytes) {
                thumbnailKey = baseKey + "_thumbnail.webp";
                rules.add(buildDerivativeRule(bucket, thumbnailKey,
                        String.format("imageMogr2/thumbnail/%dx%d>/format/webp/quality/%d",
                                thumbnailMaxWidth, thumbnailMaxHeight, thumbnailQuality)));
            }
        }
        picOperations.setRules(rules);
        return new DerivativePlan(originalKey, displayKey, thumbnailKey, picOperations);
    }

    private PicOperations.Rule buildDerivativeRule(String bucket, String outputKey, String rule) {
        PicOperations.Rule derivativeRule = new PicOperations.Rule();
        derivativeRule.setBucket(bucket);
        // COS 的 fileId 不以 / 开头时是相对原图目录；使用绝对路径避免重复拼接 public/{userId}。
        derivativeRule.setFileId("/" + StrUtil.removePrefix(outputKey, "/"));
        derivativeRule.setRule(rule);
        return derivativeRule;
    }

    private PictureUploadResult buildUploadResult(PutObjectResult putObjectResult, DerivativePlan derivativePlan,
                                                  String originalFilename, long originalSize, Long spaceId) {
        CIUploadResult ciUploadResult = putObjectResult == null ? null : putObjectResult.getCiUploadResult();
        ImageInfo imageInfo = ciUploadResult == null || ciUploadResult.getOriginalInfo() == null
                ? null : ciUploadResult.getOriginalInfo().getImageInfo();
        if (imageInfo == null || imageInfo.getWidth() <= 0 || imageInfo.getHeight() <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片元信息解析失败");
        }

        List<CIObject> processedObjects = new ArrayList<>();
        ProcessResults processResults = ciUploadResult.getProcessResults();
        if (processResults != null && CollUtil.isNotEmpty(processResults.getObjectList())) {
            processedObjects.addAll(processResults.getObjectList());
        }

        String originalUrl = targetUrl(derivativePlan.getOriginalKey(), spaceId);
        CIObject displayObject = findProcessedObject(processedObjects, derivativePlan.getDisplayKey());
        String displayUrl = originalUrl;
        long displaySize = originalSize;
        if (displayObject != null) {
            if (isSmaller(displayObject, originalSize)) {
                displayUrl = targetUrl(derivativePlan.getDisplayKey(), spaceId);
                displaySize = displayObject.getSize();
            } else {
                cleanupUnusedObject(derivativePlan.getDisplayKey(), "display-not-smaller", spaceId);
            }
        } else if (StrUtil.isNotBlank(derivativePlan.getDisplayKey())) {
            if (spaceId != null) { cleanupUnusedObject(derivativePlan.getDisplayKey(), "display-missing", spaceId); }
            log.warn("COS 未返回主展示派生图，回退原图, key: {}", derivativePlan.getDisplayKey());
        }

        CIObject thumbnailObject = findProcessedObject(processedObjects, derivativePlan.getThumbnailKey());
        String thumbnailUrl = null;
        if (thumbnailObject != null) {
            if (isSmaller(thumbnailObject, displaySize)) {
                thumbnailUrl = targetUrl(derivativePlan.getThumbnailKey(), spaceId);
            } else {
                cleanupUnusedObject(derivativePlan.getThumbnailKey(), "thumbnail-not-smaller", spaceId);
            }
        } else if (StrUtil.isNotBlank(derivativePlan.getThumbnailKey())) {
            if (spaceId != null) { cleanupUnusedObject(derivativePlan.getThumbnailKey(), "thumbnail-missing", spaceId); }
            log.warn("COS 未返回缩略派生图，前端将回退主展示图, key: {}", derivativePlan.getThumbnailKey());
        }

        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        PictureUploadResult result = new PictureUploadResult();
        result.setUrl(displayUrl);
        result.setOriginalUrl(originalUrl);
        result.setThumbnailUrl(thumbnailUrl);
        result.setPicName(FileUtil.mainName(originalFilename));
        result.setPicSize(originalSize);
        result.setPicFormat(imageInfo.getFormat());
        result.setPicColor(learn.gjx.tuku1.utils.PictureColor.normalize(imageInfo.getAve()));
        result.setPicWidth(picWidth);
        result.setPicHeight(picHeight);
        result.setPicScale(NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue());
        return result;
    }

    private CIObject findProcessedObject(List<CIObject> objects, String expectedKey) {
        if (StrUtil.isBlank(expectedKey)) {
            return null;
        }
        String normalizedExpectedKey = StrUtil.removePrefix(expectedKey, "/");
        return CollUtil.emptyIfNull(objects).stream()
                .filter(object -> object != null && StrUtil.isNotBlank(object.getKey()))
                .filter(object -> normalizedExpectedKey.equals(StrUtil.removePrefix(object.getKey(), "/")))
                .findFirst()
                .orElse(null);
    }

    private boolean isSmaller(CIObject object, long baselineSize) {
        return object.getSize() != null && object.getSize() > 0
                && (baselineSize <= 0 || object.getSize() < baselineSize);
    }

    private String removeFileSuffix(String key) {
        int slashIndex = key.lastIndexOf('/');
        int dotIndex = key.lastIndexOf('.');
        return dotIndex > slashIndex ? key.substring(0, dotIndex) : key;
    }

    private void cleanupObjectQuietly(String key, String scene) {
        if (StrUtil.isBlank(key)) {
            return;
        }
        try {
            cosManager.deleteObject(StrUtil.removePrefix(key, "/"));
        } catch (Exception cleanupException) {
            log.error("清理 COS 对象失败, scene: {}, key: {}", scene, key, cleanupException);
        }
    }

    private String targetUrl(String key, Long spaceId) {
        return spaceId == null ? getFileUrl(key) : privateStorage.url(key);
    }

    private void cleanupUnusedObject(String key, String scene, Long spaceId) {
        if (spaceId == null) { cleanupObjectQuietly(key, scene); }
        else { privateCleanup.abandon(spaceId, java.util.Collections.singletonList(targetUrl(key, spaceId))); }
    }

    /**
     * 构建 Key 对应的公网访问地址。
     */
    public String getFileUrl(String key) {
        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosClientConfig.getBucket(), cosClientConfig.getRegion(), key);
    }

    /**
     * 根据已有 URL 反推 COS Key。
     */
    public String extractKeyFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        String httpsPrefix = String.format("https://%s.cos.%s.myqcloud.com/", cosClientConfig.getBucket(), cosClientConfig.getRegion());
        if (StrUtil.startWith(url, httpsPrefix)) {
            return url.substring(httpsPrefix.length());
        }
        String httpPrefix = String.format("http://%s.cos.%s.myqcloud.com/", cosClientConfig.getBucket(), cosClientConfig.getRegion());
        if (StrUtil.startWith(url, httpPrefix)) {
            return url.substring(httpPrefix.length());
        }
        return null;
    }

    /**
     * 删除当前 COS 中的图片。外部 URL 不会被当作对象 Key 删除。
     *
     * @return 是否识别并执行了删除
     */
    public boolean deletePicture(String pictureUrl) {
        String key = extractKeyFromUrl(pictureUrl);
        if (StrUtil.isBlank(key)) {
            return false;
        }
        cosManager.deleteObject(key);
        return true;
    }

    /**
     * 校验上传图片的基础约束。
     */
    private void validatePicture(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        validatePictureSize(multipartFile.getSize());
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        validatePictureSuffix(suffix);
    }

    private void validatePictureSize(long fileSize) {
        if (fileSize <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        if (fileSize > MAX_PICTURE_SIZE) {
            log.error("文件大小超过限制, size: {}", fileSize);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");
        }
    }

    private void validatePictureSuffix(String suffix) {
        if (!isAllowedPictureSuffix(suffix)) {
            log.error("Unsupported image format, suffix: {}", suffix);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的图片格式");
        }
    }

    private boolean isAllowedPictureSuffix(String suffix) {
        return StrUtil.isNotBlank(suffix)
                && ALLOWED_PICTURE_SUFFIXES.contains(suffix.toLowerCase(Locale.ROOT));
    }

    private void validatePictureSignature(File file, String suffix) {
        byte[] header = new byte[12];
        int length;
        try (InputStream inputStream = new FileInputStream(file)) {
            length = inputStream.read(header);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取图片内容失败");
        }
        String normalizedSuffix = suffix.toLowerCase(Locale.ROOT);
        boolean valid;
        if ("jpg".equals(normalizedSuffix) || "jpeg".equals(normalizedSuffix)) {
            valid = length >= 3 && unsigned(header[0]) == 0xFF && unsigned(header[1]) == 0xD8
                    && unsigned(header[2]) == 0xFF;
        } else if ("png".equals(normalizedSuffix)) {
            valid = length >= 8 && unsigned(header[0]) == 0x89 && header[1] == 'P' && header[2] == 'N'
                    && header[3] == 'G';
        } else if ("gif".equals(normalizedSuffix)) {
            valid = length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
        } else if ("webp".equals(normalizedSuffix)) {
            valid = length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                    && header[10] == 'B' && header[11] == 'P';
        } else {
            valid = false;
        }
        if (!valid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片内容与文件格式不匹配");
        }
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private URL validateRemotePictureUrl(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 不能为空");
        }
        try {
            URI uri = new URI(fileUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 http 或 https 图片 URL");
            }
            String host = uri.getHost();
            if (StrUtil.isBlank(host)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 不合法");
            }
            validatePublicHost(host);
            return uri.toURL();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 不合法");
        }
    }

    private void validatePublicHost(String host) throws IOException {
        if ("localhost".equalsIgnoreCase(host)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许访问本机地址");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许访问内网地址");
            }
        }
    }

    private RemotePictureFile downloadRemotePicture(URL url) {
        URL currentUrl = url;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECT_COUNT; redirectCount++) {
            validateRemotePictureUrl(currentUrl.toString());
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) currentUrl.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "tuku1-picture-importer");
                int responseCode = connection.getResponseCode();
                if (isRedirect(responseCode)) {
                    String location = connection.getHeaderField("Location");
                    if (StrUtil.isBlank(location)) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "远程图片跳转地址为空");
                    }
                    currentUrl = new URL(currentUrl, location);
                    continue;
                }
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "远程图片无法访问");
                }
                String contentType = connection.getContentType();
                String suffix = getPictureSuffix(currentUrl, contentType);
                long contentLength = connection.getContentLengthLong();
                if (contentLength > MAX_PICTURE_SIZE) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "远程图片大小不能超过 2MB");
                }
                File tempFile = File.createTempFile("url_picture_", "." + suffix);
                long fileSize;
                try {
                    try (InputStream inputStream = connection.getInputStream()) {
                        fileSize = writeToTempFile(inputStream, tempFile);
                    }
                } catch (Exception e) {
                    if (tempFile.exists() && !tempFile.delete()) {
                        log.warn("远程图片临时文件删除失败: {}", tempFile.getAbsolutePath());
                    }
                    throw e;
                }
                String originalFilename = buildRemoteOriginalFilename(currentUrl, suffix);
                return new RemotePictureFile(tempFile, originalFilename, fileSize);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("远程图片下载失败, url: {}", currentUrl, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "远程图片下载失败");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "远程图片跳转次数过多");
    }

    private boolean isRedirect(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == 307
                || responseCode == 308;
    }

    private String getPictureSuffix(URL url, String contentType) {
        String suffix = FileUtil.getSuffix(url.getPath());
        if (!isAllowedPictureSuffix(suffix)) {
            suffix = getSuffixByContentType(contentType);
        }
        validatePictureSuffix(suffix);
        String normalizedContentType = normalizeContentType(contentType);
        if (StrUtil.isBlank(normalizedContentType) || !normalizedContentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "远程文件不是图片");
        }
        return suffix.toLowerCase(Locale.ROOT);
    }

    private String getSuffixByContentType(String contentType) {
        String normalizedContentType = normalizeContentType(contentType);
        if ("image/jpeg".equals(normalizedContentType)) {
            return "jpg";
        }
        if ("image/png".equals(normalizedContentType)) {
            return "png";
        }
        if ("image/gif".equals(normalizedContentType)) {
            return "gif";
        }
        if ("image/webp".equals(normalizedContentType)) {
            return "webp";
        }
        return null;
    }

    private String normalizeContentType(String contentType) {
        if (StrUtil.isBlank(contentType)) {
            return null;
        }
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private long writeToTempFile(InputStream inputStream, File tempFile) throws IOException {
        long totalSize = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream outputStream = new FileOutputStream(tempFile)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalSize += bytesRead;
                if (totalSize > MAX_PICTURE_SIZE) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "远程图片大小不能超过 2MB");
                }
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        validatePictureSize(totalSize);
        return totalSize;
    }

    private String buildRemoteOriginalFilename(URL url, String suffix) {
        String filename = FileUtil.getName(url.getPath());
        if (StrUtil.isBlank(filename) || !isAllowedPictureSuffix(FileUtil.getSuffix(filename))) {
            return "url_picture." + suffix;
        }
        return filename;
    }

    /**
     * 对已有的 COS 对象执行数据万象处理。
     *
     * @param sourceKey 原始图片的 COS Key
     * @param ruleList  处理规则列表（例如 rotate/90、thumbnail/!200x200r）
     * @param outputDir 处理后文件的输出目录，默认为 {@code processed}
     */
    public PictureProcessResult processPicture(String sourceKey, List<PictureProcessRule> ruleList, String outputDir) {
        if (StrUtil.isBlank(sourceKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "原图路径不能为空");
        }
        List<PictureProcessRule> validRules = CollUtil.emptyIfNull(ruleList).stream()
                .filter(rule -> rule != null && StrUtil.isNotBlank(rule.getRule()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(validRules)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请至少提供一条处理规则");
        }
        if (validRules.size() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "处理规则不能超过 5 条");
        }

        File tempFile = null;
        try {
            tempFile = copyRemoteFileToTemp(sourceKey);
            PicOperations picOperations = buildPicOperations(validRules, outputDir, FileUtil.getSuffix(sourceKey));
            String uploadKey = buildProcessUploadKey(sourceKey);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadKey, tempFile, picOperations);
            return buildProcessResult(putObjectResult);
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private File copyRemoteFileToTemp(String key) {
        COSObject cosObject = cosManager.getObject(key);
        if (cosObject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "原图不存在");
        }
        File tempFile;
        try {
            tempFile = File.createTempFile("process_", "." + FileUtil.getSuffix(key));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建临时文件失败");
        }
        try (COSObjectInputStream inputStream = cosObject.getObjectContent()) {
            FileUtil.writeFromStream(inputStream, tempFile);
        } catch (IOException e) {
            log.error("原图下载失败, key: {}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "拉取图片数据失败");
        }
        return tempFile;
    }

    private PicOperations buildPicOperations(List<PictureProcessRule> rules, String outputDir, String originalSuffix) {
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> cosRules = new ArrayList<>();
        String targetDir = StrUtil.isBlank(outputDir) ? "processed" : StrUtil.removeSuffix(outputDir.trim(), "/");
        targetDir = StrUtil.removePrefix(targetDir, "/");
        if (StrUtil.isBlank(targetDir)) {
            targetDir = "processed";
        }
        for (int i = 0; i < rules.size(); i++) {
            PictureProcessRule rule = rules.get(i);
            PicOperations.Rule cosRule = new PicOperations.Rule();
            cosRule.setBucket(cosClientConfig.getBucket());
            cosRule.setRule(rule.getRule());
            cosRule.setFileId(buildOutputFileId(targetDir, rule, i, originalSuffix));
            cosRules.add(cosRule);
        }
        picOperations.setRules(cosRules);
        return picOperations;
    }

    private String buildOutputFileId(String targetDir, PictureProcessRule rule, int index, String originalSuffix) {
        String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(rule.getOutputFileName()), originalSuffix);
        if (StrUtil.isBlank(suffix)) {
            suffix = "jpg";
        }
        String fileName;
        if (StrUtil.isNotBlank(rule.getOutputFileName())) {
            fileName = rule.getOutputFileName();
            if (!fileName.contains(".")) {
                fileName = fileName + "." + suffix;
            }
        } else {
            fileName = String.format("%s_%s_%d.%s",
                    DateUtil.format(new Date(), "yyyyMMddHHmmss"),
                    RandomUtil.randomString(6),
                    index,
                    suffix);
        }
        return String.format("%s/%s", targetDir, fileName);
    }

    private String buildProcessUploadKey(String sourceKey) {
        String suffix = FileUtil.getSuffix(sourceKey);
        if (StrUtil.isBlank(suffix)) {
            suffix = "tmp";
        }
        return String.format("processing/%s_%s.%s",
                DateUtil.format(new Date(), "yyyyMMddHHmmss"),
                RandomUtil.randomString(8),
                suffix);
    }

    private PictureProcessResult buildProcessResult(PutObjectResult putObjectResult) {
        PictureProcessResult result = new PictureProcessResult();
        if (putObjectResult == null) {
            return result;
        }
        CIUploadResult ciUploadResult = putObjectResult.getCiUploadResult();
        if (ciUploadResult == null) {
            return result;
        }
        result.setOriginal(PictureProcessInfoVO.fromOriginal(ciUploadResult.getOriginalInfo()));
        ProcessResults processResults = ciUploadResult.getProcessResults();
        if (processResults != null && CollUtil.isNotEmpty(processResults.getObjectList())) {
            for (CIObject ciObject : processResults.getObjectList()) {
                result.addProcessedInfo(PictureProcessInfoVO.fromProcessObject(ciObject));
            }
        }
        return result;
    }

    private static class RemotePictureFile {

        private final File file;

        private final String originalFilename;

        private final long fileSize;

        private RemotePictureFile(File file, String originalFilename, long fileSize) {
            this.file = file;
            this.originalFilename = originalFilename;
            this.fileSize = fileSize;
        }

        public File getFile() {
            return file;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public long getFileSize() {
            return fileSize;
        }
    }

    private static class DerivativePlan {

        private final String originalKey;

        private final String displayKey;

        private final String thumbnailKey;

        private final PicOperations picOperations;

        private DerivativePlan(String originalKey, String displayKey, String thumbnailKey,
                               PicOperations picOperations) {
            this.originalKey = originalKey;
            this.displayKey = displayKey;
            this.thumbnailKey = thumbnailKey;
            this.picOperations = picOperations;
        }

        public String getOriginalKey() {
            return originalKey;
        }

        public String getDisplayKey() {
            return displayKey;
        }

        public String getThumbnailKey() {
            return thumbnailKey;
        }

        public PicOperations getPicOperations() {
            return picOperations;
        }
    }
}
