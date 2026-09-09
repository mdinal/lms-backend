package com.lms.backend.multitenancy;

public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new InheritableThreadLocal<>();
    public static final String DEFAULT_TENANT = "localhost";

    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }

    public static String getTenantId() {
        String tenant = currentTenant.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    public static void clear() {
        currentTenant.remove();
    }
}
