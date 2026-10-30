package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import learn.gjx.tuku1.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;

/**
 * 对腾讯云 COS 客户端的简单封装，统一处理常见的对象操作。
 */
@Component
public class CosManager {

    /** 显式指定独立私有桶；禁止调用方通过公共桶默认值进行私有上传。 */
    public PutObjectResult putPrivatePictureObject(String bucket, String key, File file, PicOperations operations) {
        PutObjectRequest request = new PutObjectRequest(bucket, key, file);
        request.setCannedAcl(com.qcloud.cos.model.CannedAccessControlList.Private);
        request.setPicOperations(operations);
        PutObjectResult result = timedUpload(request, file == null ? 0 : file.length());
        // 派生图必须落在已配置为私有读写的桶。对返回的预期派生对象再显式设置 Private。
        if (result != null && result.getCiUploadResult() != null
                && result.getCiUploadResult().getProcessResults() != null
                && result.getCiUploadResult().getProcessResults().getObjectList() != null) {
            for (com.qcloud.cos.model.ciModel.persistence.CIObject object
                    : result.getCiUploadResult().getProcessResults().getObjectList()) {
                if (object == null || object.getKey() == null) { continue; }
                String outputKey = object.getKey().replaceFirst("^/", "");
                boolean expected = operations.getRules().stream()
                        .anyMatch(rule -> ("/" + outputKey).equals(rule.getFileId()) && bucket.equals(rule.getBucket()));
                if (expected) {
                    long aclStarted = System.nanoTime();
                    try {
                        cosClient.setObjectAcl(bucket, outputKey, com.qcloud.cos.model.CannedAccessControlList.Private);
                    } finally {
                        UploadTiming.record(UploadTiming.Stage.PRIVATE_ACL, aclStarted,
                                object.getSize() == null ? 0 : object.getSize());
                    }
                }
            }
        }
        return result;
    }

    private PutObjectResult timedUpload(PutObjectRequest request, long bytes) {
        long started = System.nanoTime();
        try {
            return cosClient.putObject(request);
        } finally {
            UploadTiming.record(UploadTiming.Stage.COS_UPLOAD, started, bytes);
        }
    }

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 以指定的对象键上传本地文件。
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, file);
        return timedUpload(putObjectRequest, file == null ? 0 : file.length());
    }

    /**
     * 上传图片并启用数据万象，获取宽高等元信息。
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        return putPictureObject(key, file, picOperations);
    }

    /**
     * 上传图片并附带自定义的数据万象处理规则。
     */
    public PutObjectResult putPictureObject(String key, File file, PicOperations picOperations) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, file);
        if (picOperations != null) {
            putObjectRequest.setPicOperations(picOperations);
        }
        return timedUpload(putObjectRequest, file == null ? 0 : file.length());
    }

    /**
     * 通过输入流上传对象。
     */
    public PutObjectResult putObject(String key, InputStream inputStream, ObjectMetadata metadata) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(), key, inputStream, metadata);
        return timedUpload(putObjectRequest, metadata == null ? 0 : metadata.getContentLength());
    }

    /**
     * 根据 key 下载对象。
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(
                cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 根据 key 删除对象。
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}
