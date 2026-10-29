package learn.gjx.tuku1.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.aop.AuthInterceptor;
import learn.gjx.tuku1.exception.GlobalExceptionHandler;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.*;
import learn.gjx.tuku1.service.impl.UserServiceImpl;
import learn.gjx.tuku1.constant.UserConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class UserPrivacyTest {
    UserServiceImpl service;
    MockMvc mvc;
    User target;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        service = spy(new UserServiceImpl());
        target = new User(); target.setId(7L); target.setUserAccount("privacy-fixture");
        target.setUserRole(1); target.setUserName("公开昵称");
        target.setUserPassword("fixture-hash");
        doReturn(target).when(service).getById(any());
        doReturn(new Page<User>(1, 10, 1).setRecords(Collections.singletonList(target)))
                .when(service).page(any(Page.class), any());
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        AuthInterceptor auth = new AuthInterceptor();
        ReflectionTestUtils.setField(auth, "userService", service);
        AspectJProxyFactory factory = new AspectJProxyFactory(controller); factory.addAspect(auth);
        learn.gjx.tuku1.service.impl.PictureServiceImpl pictures = spy(new learn.gjx.tuku1.service.impl.PictureServiceImpl());
        ReflectionTestUtils.setField(pictures, "userService", service);
        learn.gjx.tuku1.manager.space.PictureAccessManager access = mock(learn.gjx.tuku1.manager.space.PictureAccessManager.class);
        when(access.isPubliclyVisible(any())).thenReturn(true);
        ReflectionTestUtils.setField(pictures, "pictureAccessManager", access);
        learn.gjx.tuku1.model.entity.Picture picture = new learn.gjx.tuku1.model.entity.Picture();
        picture.setId(8L); picture.setUserId(7L);
        doReturn(picture).when(pictures).getById(8L);
        PictureController pictureController = new PictureController();
        ReflectionTestUtils.setField(pictureController, "pictureService", pictures);
        mvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy(), pictureController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    MockHttpSession identity(int role) {
        MockHttpSession session = new MockHttpSession();
        if (role >= 0) {
            User actor = new User(); actor.setId(99L); actor.setUserRole(role);
            actor.setUserAccount("self-fixture");
            doReturn(actor).when(service).getById(99L);
            session.setAttribute(UserConstant.USER_LOGIN_STATE, service.userLogin(actor));
        }
        return session;
    }

    JsonNode body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString());
    }
    void publicFields(JsonNode node) {
        assertTrue(node.has("userName"));
        assertFalse(node.has("userAccount")); assertFalse(node.has("userRole"));
        assertFalse(node.has("userPassword"));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void creatingUserWithoutInitialPasswordIsRejectedForEveryIdentity(int role) throws Exception {
        JsonNode response = body(post("/user/add").session(identity(role)).contentType("application/json")
                .content("{\"userAccount\":\"new-fixture\"}"));
        assertNotEquals(0, response.path("code").asInt());
        verify(service, never()).save(any(User.class));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void publicProfileNeverExposesAccountOrRole(int role) throws Exception {
        JsonNode response = body(get("/user/get/vo").param("id", "7").session(identity(role)));
        assertEquals(0, response.path("code").asInt()); publicFields(response.path("data"));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void userDirectoryIsAdminOnlyAndNeverReturnsHash(int role) throws Exception {
        JsonNode response = body(post("/user/list/page/vo").session(identity(role))
                .contentType("application/json").content("{\"current\":1,\"pageSize\":50}"));
        if (role != 1) { assertNotEquals(0, response.path("code").asInt()); verify(service, never()).page(any(Page.class), any()); }
        else {
            assertEquals(0, response.path("code").asInt());
            JsonNode record = response.path("data").path("records").get(0);
            assertTrue(record.has("userAccount")); assertTrue(record.has("userRole"));
            assertFalse(record.has("userPassword"));
        }
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void adminDetailIsRestrictedAndExcludesHash(int role) throws Exception {
        JsonNode response = body(get("/user/get").param("id", "7").session(identity(role)));
        if (role != 1) assertNotEquals(0, response.path("code").asInt());
        else { assertEquals(0, response.path("code").asInt());
            assertTrue(response.path("data").has("userAccount"));
            assertFalse(response.path("data").has("userPassword")); }
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void ownProfileKeepsOwnAccountOnlyForAuthenticatedCaller(int role) throws Exception {
        JsonNode response = body(get("/user/get/login").session(identity(role)));
        if (role < 0) assertNotEquals(0, response.path("code").asInt());
        else { assertEquals(0, response.path("code").asInt());
            assertEquals("self-fixture", response.path("data").path("userAccount").asText());
            assertFalse(response.path("data").has("userPassword")); }
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void embeddedAuthorAndMemberShapesRemainPublic(int role) throws Exception {
        JsonNode response = body(get("/picture/get/vo").param("id", "8").session(identity(role)));
        assertEquals(0, response.path("code").asInt());
        publicFields(response.path("data").path("user"));
        identity(role);
        PictureVO picture = new PictureVO(); picture.setUser(service.getUserVO(target));
        publicFields(json.valueToTree(picture).path("user"));
        SpaceUserVO member = new SpaceUserVO(); member.setUserId(target.getId());
        member.setUserName(target.getUserName());
        publicFields(json.valueToTree(member));
        assertTrue(json.valueToTree(service.userLogin(target)).has("userAccount"));
    }
}
