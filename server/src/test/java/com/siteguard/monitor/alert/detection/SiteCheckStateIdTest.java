package com.siteguard.monitor.alert.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SiteCheckStateIdTest {

    @Test
    void threeComponentKey_equalityByAllFields() {
        var a = new SiteCheckStateId(1L, "PATH_CHECK", "/api/orders");
        var b = new SiteCheckStateId(1L, "PATH_CHECK", "/api/orders");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentBucket_notEqual() {
        var a = new SiteCheckStateId(1L, "PATH_CHECK", "/api/orders");
        var b = new SiteCheckStateId(1L, "PATH_CHECK", "/api/payments");
        assertNotEquals(a, b);
    }
}
