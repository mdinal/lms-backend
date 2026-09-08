package com.lms.backend.multitenancy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        String serverName = req.getServerName();
        
        // Example: institute1.lms.com -> "institute1"
        // If localhost or top level, default to "public"
        String tenantId = extractTenantId(serverName);
        
        TenantContext.setTenantId(tenantId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantId(String serverName) {
        if (serverName == null || serverName.equals("localhost") || serverName.equals("127.0.0.1")) {
            return TenantContext.DEFAULT_TENANT;
        }
        
        // Remove www. if present
        if (serverName.startsWith("www.")) {
            serverName = serverName.substring(4);
        }
        
        // Example: institute1.com -> "institute1"
        int firstDotIndex = serverName.indexOf('.');
        if (firstDotIndex > 0) {
            return serverName.substring(0, firstDotIndex);
        }
        
        return serverName;
    }
}
