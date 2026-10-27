package learn.gjx.tuku1.service;

import cn.hutool.json.JSONUtil;
import learn.gjx.tuku1.api.aliyunai.AliyunOutpaintingClient;
import learn.gjx.tuku1.api.aliyunai.OutpaintingSize;
import learn.gjx.tuku1.config.AiEditingProperties;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.*;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Service
@Slf4j
public class AiPictureService {
    private final PictureMapper pictures; private final PictureAccessManager access;
    private final PictureService uploads; private final AiTaskStore store;
    private final AiEditingFiles files; private final AiEditingProperties config;
    public AiPictureService(PictureMapper pictures, PictureAccessManager access, PictureService uploads, AiTaskStore store,
                            AiEditingFiles files, AiEditingProperties config) {
        this.pictures=pictures;this.access=access;this.uploads=uploads;this.store=store;this.files=files;this.config=config;
    }
    public boolean enabled(User user) {return user != null && !Objects.equals(user.getIsDelete(),1) && config.isEnabled() && config.configured() && config.getAllowedUserIds().contains(user.getId());}
    public boolean privateEnabled(){return config.isPrivateEnabled();}
    public Picture editable(Long id,User user) {
        require(id!=null&&id>0,"图片 ID 无效");
        Picture picture=pictures.selectById(id);
        ThrowUtils.throwIf(picture==null || Objects.equals(picture.getIsDelete(),1),ErrorCode.NOT_FOUND_ERROR,"图片不存在");
        access.checkEdit(picture,user); return picture;
    }
    public byte[] source(Long id, User user){return files.source(editable(id,user));}
    public AiTaskVO create(AiOutpaintingRequest body,User user,String context) {
        require(body!=null,"请求不能为空");
        Picture picture=editable(body.getPictureId(),user);
        require(enabled(user),"AI 扩图未启用或当前账号未获测试额度");
        require(Boolean.TRUE.equals(body.getExternalProcessingConsent()),"请确认将图片交给阿里云百炼处理");
        require(picture.getSpaceId()==null || config.isPrivateEnabled(),"私有图片的外部 AI 处理尚未开启");
        require(body.getRequestId()!=null && body.getRequestId().matches("[0-9a-fA-F-]{36}"),"请求标识无效");
        require(scale(body.getXScale()) && scale(body.getYScale()) && (body.getXScale()>1||body.getYScale()>1),"扩图比例应为 1 至 3，至少一个方向大于 1");
        require(picture.getPicWidth()!=null && picture.getPicHeight()!=null && picture.getPicWidth()>=512 && picture.getPicHeight()>=512
                && picture.getPicWidth()<=4096 && picture.getPicHeight()<=4096,"原图宽高须在 512 至 4096 像素之间");
        int[] output;
        try { output = OutpaintingSize.calculate(picture.getPicWidth(), picture.getPicHeight(), body.getXScale(), body.getYScale()); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR,"输出宽高比须在 1:4 至 4:1 之间，输出短边至少 512 像素"); }
        require(picture.getPicFormat()!=null && Arrays.asList("jpg","jpeg","png","webp").contains(picture.getPicFormat().toLowerCase(Locale.ROOT)),"此格式不支持 AI 扩图");
        AiPictureTask task=new AiPictureTask();
        task.setId(UUID.randomUUID().toString()); task.setRequestId(body.getRequestId());task.setUserId(user.getId());task.setPictureId(picture.getId());
        task.setXScale(body.getXScale());task.setYScale(body.getYScale());task.setSourceUrl(picture.getUrl());task.setStatus("QUEUED");
        task.setProviderModel(AliyunOutpaintingClient.MODEL);task.setOutputWidth(output[0]);task.setOutputHeight(output[1]);
        task.setCreateTime(new Date());task.setExpiresAt(new Date(System.currentTimeMillis()+30*60_000));
        // The committed queue row is the work item; no paid I/O on the HTTP request thread.
        return view(store.reserve(task),context);
    }
    public AiTaskVO latest(Long pictureId,User user,String context){editable(pictureId,user);return view(store.latest(pictureId,user.getId()),context);}
    private AiPictureTask owned(String id,User user) {
        require(id!=null && id.matches("[0-9a-fA-F-]{36}"),"任务 ID 无效");
        AiPictureTask task=store.find(id,user.getId());
        ThrowUtils.throwIf(task==null,ErrorCode.NOT_FOUND_ERROR,"任务不存在");
        editable(task.getPictureId(),user);return task;
    }
    public AiTaskVO get(String id,User user,String context) {
        owned(id,user);
        store.expireStale(id,new Date()); // DB only. Browser refreshes can never submit provider requests.
        return view(store.find(id,user.getId()),context);
    }
    public byte[] result(String id,User user) {
        AiPictureTask task=owned(id,user);require("SUCCEEDED".equals(task.getStatus()),"任务尚无可预览结果");
        require(task.getExpiresAt().after(new Date()),"结果已过期");return files.result(task.getResultUrl());
    }
    public PictureVO apply(String id,User user,HttpServletRequest request) {
        AiPictureTask task=owned(id,user);Picture picture=editable(task.getPictureId(),user);
        if("APPLIED".equals(task.getStatus()))return uploads.getPictureVO(picture,request);
        require("SUCCEEDED".equals(task.getStatus())&&task.getExpiresAt().after(new Date()),"任务不可应用或已过期");
        require(Objects.equals(task.getSourceUrl(),picture.getUrl()),"原图已被替换，请刷新后重新生成");
        // Validate/download before claiming: retrying these read-only steps is safe.
        org.springframework.web.multipart.MultipartFile file=files.uploadFile(files.result(task.getResultUrl()));
        require(store.transition(id,"SUCCEEDED","APPLYING","正在保存；网络中断后请先刷新查询，不要重复提交"),"结果正在保存，请刷新查询");
        PictureUploadRequest upload=new PictureUploadRequest();upload.setId(picture.getId());upload.setSpaceId(picture.getSpaceId());
        upload.setName(picture.getName());upload.setIntroduction(picture.getIntroduction());upload.setCategory(picture.getCategory());
        if(picture.getTags()!=null)upload.setTags(JSONUtil.toList(picture.getTags(),String.class));
        try {
            uploads.uploadPicture(file,upload,user);
            store.transition(id,"APPLYING","APPLIED","结果已应用");
            return uploads.getPictureVO(editable(picture.getId(),user),request);
        } catch(RuntimeException e) {
            store.transition(id,"APPLYING","APPLY_UNKNOWN","保存未确认，请刷新图片并联系管理员核对，不要重复应用");
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"保存未确认，请刷新检查图片后再处理");
        }
    }
    private AiTaskVO view(AiPictureTask task,String context) {
        if(task==null)return null;AiTaskVO vo=new AiTaskVO();vo.setId(task.getId());vo.setPictureId(task.getPictureId());
        vo.setStatus(task.getStatus());vo.setMessage(task.getMessage());vo.setCreateTime(task.getCreateTime());vo.setExpiresAt(task.getExpiresAt());
        if("SUCCEEDED".equals(task.getStatus())) {
            if(task.getExpiresAt().before(new Date())) {vo.setStatus("EXPIRED");vo.setMessage("结果已过期");}
            else vo.setResultUrl(context+"/picture/out_painting/result?taskId="+task.getId());
        }
        return vo;
    }
    private static boolean scale(Double d){return d!=null&&Double.isFinite(d)&&d>=1&&d<=3;}
    private static void require(boolean ok,String message){ThrowUtils.throwIf(!ok,ErrorCode.PARAMS_ERROR,message);}
}
