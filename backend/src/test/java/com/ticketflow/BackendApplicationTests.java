package com.ticketflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import com.ticketflow.integration.IsolatedMongoTestInitializer;

@SpringBootTest
@ContextConfiguration(initializers = IsolatedMongoTestInitializer.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
