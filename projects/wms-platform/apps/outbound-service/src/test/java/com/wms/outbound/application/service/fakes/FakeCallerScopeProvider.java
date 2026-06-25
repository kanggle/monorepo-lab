package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.CallerScopeProvider;
import com.wms.outbound.application.security.CallerScope;

/**
 * Test fake for {@link CallerScopeProvider}. Defaults to
 * {@link CallerScope#unrestricted()} (the native-wms / internal behaviour, so
 * existing service unit tests are unaffected); call {@link #restrictTo} to
 * exercise the cross-tenant scoping path (TASK-MONO-304).
 */
public class FakeCallerScopeProvider implements CallerScopeProvider {

    private CallerScope scope = CallerScope.unrestricted();

    public void restrictTo(String tenantId) {
        this.scope = CallerScope.restrictedTo(tenantId);
    }

    public void unrestrict() {
        this.scope = CallerScope.unrestricted();
    }

    @Override
    public CallerScope current() {
        return scope;
    }
}
