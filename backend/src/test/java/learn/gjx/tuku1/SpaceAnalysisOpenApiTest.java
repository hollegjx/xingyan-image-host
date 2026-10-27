package learn.gjx.tuku1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 从真实 Controller/DTO 生成完整契约，不手工维护另一份前端接口声明。 */
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.config.location=classpath:application.example.yml",
        "spring.datasource.url=jdbc:h2:mem:openapi_context;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "server.servlet.context-path=/api", "spring.session.store-type=none",
        "picture.draft.cleanup-enabled=false", "picture.ai-editing.enabled=false",
        "picture.private-storage.enabled=false", "knife4j.enable=true", "knife4j.openapi.title=图库接口",
        "knife4j.openapi.version=v1.0", "knife4j.openapi.group.default.api-rule=package",
        "knife4j.openapi.group.default.api-rule-resources[0]=learn.gjx.tuku1.controller",
        "spring.mvc.pathmatch.matching-strategy=ant_path_matcher"
})
class SpaceAnalysisOpenApiTest {
    @Autowired private MockMvc mvc;
    @Test void fullContractIncludesAnalysisAndExistingPictureApis() throws Exception {
        String json = mvc.perform(get("/api/v2/api-docs").contextPath("/api").param("group", "default"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(json.contains("/space/analyze/dashboard"));
        assertTrue(json.contains("/picture/upload"));
        assertTrue(json.contains("SpaceAnalyzeRequest"));
        assertTrue(json.contains("/spaceUser/my/list/page"));
        assertTrue(json.contains("permissionList"));
        String output = System.getProperty("analysis.openapi.output");
        if (output != null) Files.write(Paths.get(output), json.getBytes(StandardCharsets.UTF_8));
    }
}
