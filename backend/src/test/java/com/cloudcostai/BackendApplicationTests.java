package com.cloudcostai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BackendApplicationTests {
	@Autowired private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void costExplorerEndpointIsEnabledByDefault() {
		assertThat(applicationContext.getBeansOfType(com.cloudcostai.aws.service.AwsLiveCostService.class)).isNotEmpty();
	}

}
