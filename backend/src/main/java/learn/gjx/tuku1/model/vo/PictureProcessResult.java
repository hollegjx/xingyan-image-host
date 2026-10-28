package learn.gjx.tuku1.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片处理结果
 */
@Data
public class PictureProcessResult implements Serializable {

    private PictureProcessInfoVO original;

    private List<PictureProcessInfoVO> processedList = new ArrayList<>();

    private java.util.Date expiresAt;

    public void addProcessedInfo(PictureProcessInfoVO infoVO) {
        if (infoVO != null) {
            processedList.add(infoVO);
        }
    }

    private static final long serialVersionUID = 1L;
}

