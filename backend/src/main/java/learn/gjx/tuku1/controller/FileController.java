package learn.gjx.tuku1.controller;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.manager.CosManager;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 文件接口（仅用于测试）
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 测试文件上传（仅管理员）
     *
     * @param multipartFile 上传的文件
     * @return 文件访问路径
     */
    @PostMapping("/test/upload")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<String> testFileUpload(@RequestPart("file") MultipartFile multipartFile) {
        // 校验文件
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 获取文件信息
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);

        // 生成存储路径：test/uuid.后缀
        String key = String.format("test/%s.%s", UUID.randomUUID(), suffix);

        File tempFile = null;
        try {
            // 创建临时文件
            tempFile = File.createTempFile("upload_", "." + suffix);
            multipartFile.transferTo(tempFile);

            // 上传到 COS
            cosManager.putObject(key, tempFile);

            // 拼接文件访问路径
            String filePath = String.format("https://%s.cos.%s.myqcloud.com/%s",
                    cosClientConfig.getBucket(), cosClientConfig.getRegion(), key);
            log.info("文件上传成功, key: {}, filePath: {}", key, filePath);

            return BaseResponse.success(filePath);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        } finally {
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 测试文件下载（仅管理员）
     *
     * @param filepath 文件路径（如 test/xxx.jpg）
     * @param response HTTP 响应对象
     */
    @GetMapping("/test/download")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public void testFileDownload(String filepath, HttpServletResponse response) throws IOException {
        if (filepath == null || filepath.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件路径不能为空");
        }

        COSObjectInputStream inputStream = null;
        try {
            COSObject cosObject = cosManager.getObject(filepath);
            inputStream = cosObject.getObjectContent();
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath.substring(filepath.lastIndexOf("/") + 1));
            // 写入响应流
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
            response.getOutputStream().flush();
            log.info("文件下载成功, filepath: {}", filepath);
        } catch (Exception e) {
            log.error("文件下载失败, filepath: {}", filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件下载失败");
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}
