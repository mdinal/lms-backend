package com.lms.backend;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.sql.DataSource;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.cloud.aws.secretsmanager.enabled=false"
})
class BackendApplicationTests {

    @MockBean
    private DataSource dataSource;

    @MockBean(name = "publicLiquibase")
    private SpringLiquibase publicLiquibase;

    @MockBean(name = "tenantLiquibase")
    private SpringLiquibase tenantLiquibase;

    @Test
    void contextLoads() {
    }

}
