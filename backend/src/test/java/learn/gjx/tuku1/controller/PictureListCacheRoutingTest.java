package learn.gjx.tuku1.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Routing-only tests; authorization continues to be enforced by the existing AuthCheck aspect. */
class PictureListCacheRoutingTest {
    @Test
    void onlyPublicEndpointUsesCachedServiceEntry() {
        PictureService service = mock(PictureService.class);
        UserService users = mock(UserService.class);
        PictureController controller = new PictureController();
        ReflectionTestUtils.setField(controller, "pictureService", service);
        ReflectionTestUtils.setField(controller, "userService", users);
        MockHttpServletRequest request = new MockHttpServletRequest();
        PictureQueryRequest query = new PictureQueryRequest();
        controller.listPictureVOByPage(query, request, new org.springframework.mock.web.MockHttpServletResponse());
        verify(service).listPublicPicturePage(query, request);
        clearInvocations(service);

        User user = new User();
        user.setId(42L);
        when(users.getLoginUser(request)).thenReturn(user);
        when(service.page(any(Page.class), any())).thenReturn(new Page<Picture>());
        controller.listPictureByPage(new PictureQueryRequest(), request);
        controller.listPictureReviewByPage(new PictureQueryRequest(), request);
        controller.listMyPictureVOByPage(new PictureQueryRequest(), request);
        verify(service, times(3)).page(any(Page.class), any());
        verify(service, never()).listPublicPicturePage(any(), any());
    }
}
