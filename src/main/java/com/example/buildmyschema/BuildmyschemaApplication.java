package com.example.buildmyschema;

import com.example.buildmyschema.service.AiService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BuildmyschemaApplication {
	@Value("${string.test}")
	private String stringTest;

	public static void main(String[] args) {

		SpringApplication.run(BuildmyschemaApplication.class, args);

	}
	@PostConstruct
	public void init() {
		System.out.println(stringTest);
	}

	@Bean
    CommandLineRunner runner(AiService aiService) {
		return args -> aiService.testAi();
	}

}
