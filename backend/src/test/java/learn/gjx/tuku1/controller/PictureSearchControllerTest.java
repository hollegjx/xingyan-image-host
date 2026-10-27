package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.model.dto.picture.SearchPictureByPictureRequest;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PictureSearchControllerTest {
    PictureSearchService service;
    UserService users;
    User current;
    MockMvc mvc;
    @BeforeEach void setup() {
        service = mock(PictureSearchService.class); users = mock(UserService.class);
        current = new User(); current.setId(19L);
        when(users.getLoginUser(any())).thenReturn(current);
        when(service.search(any(), eq(current))).thenReturn(Collections.emptyList());
        mvc = MockMvcBuilders.standaloneSetup(new PictureSearchController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void usesSessionIdentityAndPreservesLongId() throws Exception {
        mvc.perform(post("/picture/search/picture").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pictureId\":\"9007199254740997\",\"externalSearchConsent\":true,\"userId\":1,\"url\":\"https://evil.example\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").isArray())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        ArgumentCaptor<SearchPictureByPictureRequest> dto = ArgumentCaptor.forClass(SearchPictureByPictureRequest.class);
        verify(service).search(dto.capture(), same(current));
        assertEquals(9007199254740997L, dto.getValue().getPictureId());
        assertTrue(dto.getValue().getExternalSearchConsent());
        assertTrue(PictureSearchController.class.getMethod("searchPictureByPicture", SearchPictureByPictureRequest.class,
                javax.servlet.http.HttpServletRequest.class, javax.servlet.http.HttpServletResponse.class)
                .isAnnotationPresent(AuthCheck.class));
    }

    @Test void absentSessionDoesNotCallSearch() throws Exception {
        when(users.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        mvc.perform(post("/picture/search/picture").contentType(MediaType.APPLICATION_JSON).content("{\"pictureId\":2}"))
                .andExpect(jsonPath("$.code").value(40100));
        verifyNoInteractions(service);
    }

    @Test void malformedLongNeverReachesService() throws Exception {
        mvc.perform(post("/picture/search/picture").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pictureId\":\"9999999999999999999999\"}"))
                .andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(service);
    }

    @Test void unavailableIsNotPresentedAsEmptySuccess() throws Exception {
        when(service.search(any(), any())).thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "第三方服务暂不可用"));
        mvc.perform(post("/picture/search/picture").contentType(MediaType.APPLICATION_JSON).content("{\"pictureId\":2}"))
                .andExpect(jsonPath("$.code").value(50300)).andExpect(jsonPath("$.data").doesNotExist());
    }
}
