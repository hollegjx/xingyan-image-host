package learn.gjx.tuku1.service;

import learn.gjx.tuku1.aop.AuthInterceptor;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.controller.*;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.mapper.PictureMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.UserMapper;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PictureFavoriteIntegrationTest {
    static ConfigurableApplicationContext app;
    static JdbcTemplate jdbc;
    PictureFavoriteService favorites;
    MockMvc mvc;
    static com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
    @BeforeAll static void start() throws Exception {
        app = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.SERVLET).run(
                "--spring.config.location=optional:classpath:favorite-empty.yml", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.session.store-type=none", "--spring.datasource.url=jdbc:h2:mem:favorites;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--mybatis-plus.configuration.map-underscore-to-camel-case=false", "--mybatis-plus.global-config.db-config.logic-delete-field=isDelete",
                "--logging.level.root=WARN");
        jdbc = app.getBean(JdbcTemplate.class);
        for (String file : Arrays.asList("sql/create_sql.sql", "sql/alter_picture_favorite.sql")) {
            String ddl = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8)
                .replaceFirst("(?i)use\\s+tuku\\s*;", "")
                .replaceAll("(?i)\\s+collate\\s*=?\\s*utf8mb4_unicode_ci", "")
                .replaceAll("(?i)\\s+comment\\s*=?\\s*'[^']*'", "")
                .replaceAll("(?i)NOT NULL\\s+ON UPDATE CURRENT_TIMESTAMP", "ON UPDATE CURRENT_TIMESTAMP NOT NULL")
                .replace("ENGINE=InnoDB", "").replace("DEFAULT CHARSET=utf8mb4", "");
            new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))).execute(app.getBean(javax.sql.DataSource.class));
        }
    }
    @AfterAll static void stop() { if (app != null) app.close(); }
    @BeforeEach void resetData() {
        jdbc.update("DELETE FROM picture_favorite"); jdbc.update("DELETE FROM picture"); jdbc.update("DELETE FROM user");
        for (long id=1;id<=3;id++) jdbc.update("INSERT INTO user(id,userAccount,userPassword,userRole,userName) VALUES(?,?,?,?,'fixture')",id,"fixture-"+id,"unused",id==3?1:0);
        for (long id=10;id<=14;id++) jdbc.update("INSERT INTO picture(id,name,userId,uploadStatus,reviewStatus,url,originalUrl) VALUES(?,?,1,1,1,'/fixture.webp','/fixture.webp')",id,"公开图片"+id);
        favorites=app.getBean(PictureFavoriteService.class);
        mvc=MockMvcBuilders.webAppContextSetup((WebApplicationContext)app).build();
        UserService users=app.getBean(UserService.class);reset(users);
        when(users.getLoginUser(any())).thenAnswer(i->{
            javax.servlet.http.HttpServletRequest r=i.getArgument(0);String id=r.getHeader("X-Test-User");
            if(id==null)throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);return user(Long.parseLong(id));
        });
        PictureService pictures=app.getBean(PictureService.class);reset(pictures);
        when(pictures.getById(any(java.io.Serializable.class))).thenAnswer(i->app.getBean(PictureMapper.class).selectById(i.getArgument(0)));
        when(pictures.getPictureVO(any(),any())).thenAnswer(i->PictureVO.objToVo(i.getArgument(0)));
        when(pictures.getPictureVOList(any(),any())).thenAnswer(i->{
            List<Picture> rows=i.getArgument(0);List<PictureVO> views=new ArrayList<>();rows.forEach(p->views.add(PictureVO.objToVo(p)));return views;
        });
    }
    User user(long id){return app.getBean(UserMapper.class).selectById(id);}
    PictureFavoriteRequest request(long id,boolean value){PictureFavoriteRequest r=new PictureFavoriteRequest();r.setPictureId(id);r.setFavorite(value);return r;}
    @Test void duplicateSetAndUnsetAreIdempotentAndCountsAreReliable(){
        assertTrue(favorites.set(request(10,true),user(1)).isFavorited());
        assertEquals(1,favorites.set(request(10,true),user(1)).getFavoriteCount());
        assertEquals(2,favorites.set(request(10,true),user(2)).getFavoriteCount());
        assertFalse(favorites.set(request(10,false),user(1)).isFavorited());
        assertEquals(1,favorites.set(request(10,false),user(1)).getFavoriteCount());
        assertTrue(favorites.contains(2,10));assertFalse(favorites.contains(1,10));
    }
    @ParameterizedTest @ValueSource(strings={"spaceId=99","reviewStatus=0","reviewStatus=2","uploadStatus=0","isDelete=1"})
    void nonPublicPicturesCannotBeFavorited(String change){
        jdbc.update("UPDATE picture SET "+change+" WHERE id=10");
        assertThrows(BusinessException.class,()->favorites.set(request(10,true),user(1)));
        assertEquals(0,favorites.count(10));
    }
    @Test void invalidAndMissingPicturesAreRejected(){
        assertThrows(BusinessException.class,()->favorites.set(request(999,true),user(1)));
        assertThrows(BusinessException.class,()->favorites.set(request(-1,true),user(1)));
        assertThrows(BusinessException.class,()->favorites.set(new PictureFavoriteRequest(),user(1)));
        assertThrows(BusinessException.class,()->favorites.set(request(10,true),null));
    }
    @Test void databaseEnforcesUserPictureUniqueness(){
        favorites.set(request(10,true),user(1));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO picture_favorite(userId,pictureId) VALUES(1,10)"));
    }
    @Test void concurrentDuplicatesCreateOnlyOneRecord() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(4);CountDownLatch start=new CountDownLatch(1);
        try{List<Future<?>> results=new ArrayList<>();for(int i=0;i<4;i++)results.add(pool.submit(()->{start.await();return favorites.set(request(10,true),user(1));}));
            start.countDown();for(Future<?> result:results)result.get(10,TimeUnit.SECONDS);assertEquals(1,favorites.count(10));
        }finally{pool.shutdownNow();}
    }
    @Test void mineIsPaginatedAndIsolatedToCurrentUser(){
        for(long id=10;id<=14;id++)favorites.set(request(id,true),user(1));
        favorites.set(request(10,true),user(2));FavoritePageRequest r=new FavoritePageRequest();r.setPageSize(2);
        assertEquals(5,favorites.mine(r,user(1),new MockHttpServletRequest()).getTotal());
        List<PictureVO> first=favorites.mine(r,user(1),new MockHttpServletRequest()).getRecords();r.setCurrent(2);
        List<PictureVO> second=favorites.mine(r,user(1),new MockHttpServletRequest()).getRecords();
        assertEquals(2,first.size());assertEquals(2,second.size());assertTrue(first.stream().noneMatch(a->second.stream().anyMatch(b->a.getId().equals(b.getId()))));
        assertEquals(1,favorites.mine(r,user(2),new MockHttpServletRequest()).getTotal());
        assertEquals(0,favorites.mine(r,user(3),new MockHttpServletRequest()).getTotal());
        r.setPageSize(51);assertThrows(BusinessException.class,()->favorites.mine(r,user(1),new MockHttpServletRequest()));
    }
    @ParameterizedTest @ValueSource(strings={"spaceId=99","reviewStatus=2","uploadStatus=0","isDelete=1"})
    void hiddenPicturesDisappearAndCanStillBeRemoved(String change){
        favorites.set(request(10,true),user(1));jdbc.update("UPDATE picture SET "+change+" WHERE id=10");
        assertEquals(0,favorites.mine(new FavoritePageRequest(),user(1),new MockHttpServletRequest()).getTotal());
        assertTrue(favorites.mine(new FavoritePageRequest(),user(1),new MockHttpServletRequest()).getRecords().isEmpty());
        assertEquals(0,favorites.set(request(10,false),user(1)).getFavoriteCount());assertEquals(0,favorites.count(10));
    }
    @ParameterizedTest @ValueSource(ints={0,1,3})
    void endpointsRequireLoginForBothOrdinaryAndAdminUsers(int id)throws Exception{
        for(String action:new String[]{"set","mine"}){
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r=post("/picture/favorite/"+action).contentType("application/json")
                .content(action.equals("set")?"{\"pictureId\":10,\"favorite\":true,\"userId\":2}":"{\"userId\":2}");
            if(id>0)r.header("X-Test-User",String.valueOf(id));
            org.springframework.test.web.servlet.MvcResult result=mvc.perform(r).andReturn();
            assertEquals(id==0?40100:0,json.readTree(result.getResponse().getContentAsString()).path("code").asInt());
            if(id>0)assertEquals("private, no-store",result.getResponse().getHeader("Cache-Control"));
        }
        assertFalse(favorites.contains(2,10));
    }
    @Test void detailsPersonalizeOnlyAuthenticatedResponsesAndNeverThePublicListVo()throws Exception{
        favorites.set(request(10,true),user(1));
        for(int id:new int[]{0,1,2,3}){
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/picture/get/vo").param("id","10");
            if(id>0)r.header("X-Test-User",String.valueOf(id)).session(new MockHttpSession());
            org.springframework.test.web.servlet.MvcResult result=mvc.perform(r).andReturn();
            com.fasterxml.jackson.databind.JsonNode data=json.readTree(result.getResponse().getContentAsString()).path("data");
            assertEquals(1,data.path("favoriteCount").asInt());assertEquals(id>0,data.has("favorited"));
            if(id>0)assertEquals(id==1,data.path("favorited").asBoolean());
            assertEquals("private, no-store",result.getResponse().getHeader("Cache-Control"));
        }
        assertFalse(json.valueToTree(new PictureVO()).has("favorited"));
        assertFalse(json.valueToTree(favorites.mine(new FavoritePageRequest(),user(1),new MockHttpServletRequest()).getRecords().get(0)).has("favorited"));
    }
    @Test void expiredSessionIsAnonymousForPublicDetail(){
        Picture p=app.getBean(PictureMapper.class).selectById(10);PictureDetailVO v=new PictureDetailVO();
        MockHttpServletRequest request=new MockHttpServletRequest();request.setSession(new MockHttpSession());favorites.decorate(v,p,request);
        assertNull(v.getFavorited());assertEquals(0,v.getFavoriteCount());
    }
    @TestConfiguration @EnableAutoConfiguration @EnableAspectJAutoProxy
    @Import({MybatisPlusConfig.class,JsonConfig.class,PictureFavoriteService.class,PictureAccessManager.class,PictureFavoriteController.class,PictureController.class,AuthInterceptor.class,GlobalExceptionHandler.class})
    static class TestApplication {
        @Bean UserService users(){return mock(UserService.class);}
        @Bean PictureService pictures(){return mock(PictureService.class);}
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor externalFiles(){
            return factory -> factory.registerSingleton("privatePictureFileManager",mock(learn.gjx.tuku1.manager.PrivatePictureFileManager.class));
        }
    }
}
