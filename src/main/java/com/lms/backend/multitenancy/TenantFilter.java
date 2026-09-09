package com.lms.backend.multitenancy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class TenantFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(TenantFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        
        // Inspect proxy headers first, then Host header, then serverName
        String host = req.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = req.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = req.getServerName();
        }
        
        String tenantId = extractTenantId(host);
        
        // If host was localhost or generic, inspect Origin/Referer to check if request came from a tenant UI
        if ("localhost".equals(tenantId)) {
            String origin = req.getHeader("Origin");
            if (origin == null || origin.isBlank()) {
                origin = req.getHeader("Referer");
            }
            if (origin != null && !origin.isBlank()) {
                String cleanOrigin = origin.replace("https://", "").replace("http://", "");
                int slashIndex = cleanOrigin.indexOf('/');
                if (slashIndex > 0) {
                    cleanOrigin = cleanOrigin.substring(0, slashIndex);
                }
                String originTenant = extractTenantId(cleanOrigin);
                if (!"localhost".equals(originTenant)) {
                    tenantId = originTenant;
                }
            }
        }
        
        logger.debug("Resolved tenant: {} for request URI: {} (host: {})", tenantId, req.getRequestURI(), host);
        TenantContext.setTenantId(tenantId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantId(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return "localhost";
        }
        
        // Strip port if present (e.g., api.cambridgesuccesscentre.com:443 or localhost:8080)
        if (serverName.contains(":")) {
            serverName = serverName.split(":")[0];
        }
        
        serverName = serverName.trim().toLowerCase();
        
        if (serverName.equals("localhost") || serverName.equals("127.0.0.1")) {
            return "localhost";
        }
        
        // Remove www. if present
        if (serverName.startsWith("www.")) {
            serverName = serverName.substring(4);
        }
        // Remove api. if present
        if (serverName.startsWith("api.")) {
            serverName = serverName.substring(4);
        }
        
        // Example: cambridgesuccesscentre.com -> "cambridgesuccesscentre"
        int firstDotIndex = serverName.indexOf('.');
        if (firstDotIndex > 0) {
            return serverName.substring(0, firstDotIndex);
        }
        
        return serverName;
    }
}
