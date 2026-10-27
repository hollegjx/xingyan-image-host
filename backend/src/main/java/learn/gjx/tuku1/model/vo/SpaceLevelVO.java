package learn.gjx.tuku1.model.vo;

import learn.gjx.tuku1.model.enums.SpaceLevelEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class SpaceLevelVO {
    private int value;
    private String text;
    private long maxCount;
    private long maxSize;

    public static List<SpaceLevelVO> listLevels() {
        return Arrays.stream(SpaceLevelEnum.values())
                .map(level -> new SpaceLevelVO(level.getValue(), level.getText(),
                        level.getMaxCount(), level.getMaxSize()))
                .collect(Collectors.toList());
    }
}
