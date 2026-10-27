package learn.gjx.tuku1.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.Space;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.SpaceVO;

/** 空间元数据服务；创建防重当前为单后端实例方案。 */
public interface SpaceService extends IService<Space> {
    long addSpace(SpaceAddRequest request, User loginUser);
    Space getSpaceForAdmin(Long id, User loginUser);
    SpaceVO getSpaceVO(Long id, User loginUser);
    Page<Space> listSpaces(SpaceQueryRequest query, User loginUser);
    Page<SpaceVO> listMySpaces(SpaceQueryRequest query, User loginUser);
    boolean editSpace(SpaceEditRequest request, User loginUser);
    boolean updateSpace(SpaceUpdateRequest request, User loginUser);
    boolean deleteEmptySpace(Long id, User loginUser);
}
