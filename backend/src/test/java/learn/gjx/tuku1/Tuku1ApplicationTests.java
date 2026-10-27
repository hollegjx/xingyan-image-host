package learn.gjx.tuku1;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.config.location=classpath:application.example.yml",
        "spring.datasource.url=jdbc:h2:mem:boot_context;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "picture.draft.cleanup-enabled=false",
        "spring.session.store-type=none"
})
class Tuku1ApplicationTests {

    @Test
    void contextLoads() {
    }

}
