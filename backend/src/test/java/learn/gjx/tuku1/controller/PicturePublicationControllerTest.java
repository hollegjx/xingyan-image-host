package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.service.*;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.PrivatePictureLinkVO;
import learn.gjx.tuku1.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Date;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PicturePublicationControllerTest {
    PictureService publication;
    UserService users;
    User actor;
    MockMvc mvc;
    @BeforeEach void setup() {
        publication=mock(PictureService.class);users=mock(UserService.class);actor=new User();actor.setId(1L);
        PictureController controller=new PictureController();ReflectionTestUtils.setField(controller,"pictureService",publication);ReflectionTestUtils.setField(controller,"userService",users);
        when(users.getLoginUser(any())).thenReturn(actor);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void temporaryResponseIsPrivateNoStoreAndDoesNotAcceptVariantOrUserIdentity() throws Exception {
        when(publication.temporaryPrivateLink(10L,24,actor)).thenReturn(new PrivatePictureLinkVO("opaque-test-value",new Date()));
        mvc.perform(post("/picture/private/link").contentType("application/json").content("{\"pictureId\":10,\"validHours\":24,\"variant\":\"original\",\"userId\":999}"))
            .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.url").exists())
            .andExpect(header().string("Cache-Control","private, no-store")).andExpect(header().string("Vary","Cookie"))
            .andExpect(header().string("Referrer-Policy","no-referrer"));
        verify(publication).temporaryPrivateLink(10L,24,actor);
    }
    @Test void submitReturnsOnlyNewPictureId() throws Exception {
        when(publication.submitPublicPicture(10L,actor)).thenReturn(101L);
        mvc.perform(post("/picture/submit/public").contentType("application/json").content("{\"pictureId\":10}"))
            .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(101));
    }
    @Test void anonymousCannotReachEitherService() throws Exception {
        when(users.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        for(String path:new String[]{"/picture/submit/public","/picture/private/link"})
            mvc.perform(post(path).contentType("application/json").content("{\"pictureId\":10,\"validHours\":1}"))
                .andExpect(jsonPath("$.code").value(40100));
        verifyNoInteractions(publication);
    }
}
