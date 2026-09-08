package com.lms.backend.config;

import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class MultiTenantLiquibaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(MultiTenantLiquibaseConfig.class);

    @Value("${spring.liquibase.change-log:classpath:/db/changelog/changelog-master-public.yaml}")
    private String publicChangelog;

    @Value("${spring.liquibase.tenant-change-log:classpath:/db/changelog/changelog-master-tenant.yaml}")
    private String tenantChangelog;

    @Bean
    public SpringLiquibase publicLiquibase(DataSource dataSource) {
        logger.info("Running Liquibase for PUBLIC schema");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(publicChangelog);
        liquibase.setDefaultSchema("public");
        liquibase.setShouldRun(true);
        return liquibase;
    }

    @Bean
    @DependsOn("publicLiquibase")
    public SpringLiquibase tenantLiquibase(DataSource dataSource) {
        logger.info("Running Liquibase for all TENANT schemas");
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT domain FROM public.institutes")) {
             
            List<String> tenants = new ArrayList<>();
            while (resultSet.next()) {
                tenants.add(resultSet.getString("domain"));
            }

            for (String tenant : tenants) {
                String schemaName = "tenant_" + tenant;
                logger.info("Executing Liquibase for tenant schema: {}", schemaName);
                
                // Create schema if it doesn't exist
                try (Statement createStmt = connection.createStatement()) {
                    createStmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
                }
                
                SpringLiquibase liquibase = new SpringLiquibase();
                liquibase.setDataSource(dataSource);
                liquibase.setChangeLog(tenantChangelog);
                liquibase.setDefaultSchema(schemaName);
                liquibase.setShouldRun(true);
                // Need to manually trigger since we are inside a @Bean definition loop
                liquibase.afterPropertiesSet();
            }
            
        } catch (Exception e) {
            logger.error("Failed to run tenant migrations", e);
            throw new RuntimeException("Tenant migration failed", e);
        }
        
        // Return a dummy disabled bean to satisfy Spring context
        SpringLiquibase dummy = new SpringLiquibase();
        dummy.setShouldRun(false);
        return dummy;
    }
}
