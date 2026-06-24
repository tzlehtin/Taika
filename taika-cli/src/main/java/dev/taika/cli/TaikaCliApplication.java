package dev.taika.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "dev.taika")
public class TaikaCliApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaikaCliApplication.class, args);
    }

}