package learn.gjx.tuku1;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("learn.gjx.tuku1.mapper")
@EnableScheduling
public class Tuku1Application {

    public static void main(String[] args) {
        SpringApplication.run(Tuku1Application.class, args);
    }

}
