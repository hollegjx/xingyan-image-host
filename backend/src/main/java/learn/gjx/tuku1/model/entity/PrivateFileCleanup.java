package learn.gjx.tuku1.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/** 持久化的私有文件清理任务，同时作为上传到落库之间的临时文件凭据。 */
@Data
@TableName("private_file_cleanup")
public class PrivateFileCleanup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private String url;
    private Date nextAttemptTime;
    private Integer attempts;
}
