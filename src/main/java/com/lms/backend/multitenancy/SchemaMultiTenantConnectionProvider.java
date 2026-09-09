package com.lms.backend.multitenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Logger logger = LoggerFactory.getLogger(SchemaMultiTenantConnectionProvider.class);
    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        // The default schema for PostgreSQL is public, and we use prefix like "tenant_" for other institutes.
        // For security, you should validate the tenantIdentifier against a list of known tenants here,
        // or ensure it doesn't contain malicious SQL. 
        // We assume valid identifiers are alphanumeric.
        String schemaName = "public".equals(tenantIdentifier) ? "public" : "tenant_" + tenantIdentifier.replaceAll("[^a-zA-Z0-9]", "");
        
        try {
            logger.info("Switching JDBC connection schema to: {} (tenant: {})", schemaName, tenantIdentifier);
            connection.createStatement().execute("SET search_path TO " + schemaName);
        } catch (SQLException e) {
            throw new SQLException("Could not alter JDBC connection to specified schema [" + tenantIdentifier + "]", e);
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        if (connection != null) {
            try {
                // Reset to default schema after the connection is returned to the pool
                connection.createStatement().execute("SET search_path TO public");
            } catch (SQLException e) {
                logger.warn("Failed to reset search_path to public: {}", e.getMessage());
            } finally {
                connection.close();
            }
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }
    
    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType)
                || SchemaMultiTenantConnectionProvider.class.isAssignableFrom(unwrapType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        return null;
    }
}
