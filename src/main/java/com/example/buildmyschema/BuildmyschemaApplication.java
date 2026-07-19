package com.example.buildmyschema;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

}
