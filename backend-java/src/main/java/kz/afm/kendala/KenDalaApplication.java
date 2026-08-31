package kz.afm.kendala;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class KenDalaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KenDalaApplication.class, args);
    }
}
