package com.example.buildmyschema;

import com.example.buildmyschema.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;

class BuildmyschemaApplicationTests {

	@Test
	void contextLoads() throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		JsonNode node = mapper.readTree(
				getClass().getClassLoader().getResourceAsStream("test.json")
		);
		String jsonString = mapper.writeValueAsString(node);
		System.out.println(jsonString);
	}

}
