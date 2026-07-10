package com.siteguard.system.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsecutiveFailureConfigTest {

    @Test
    void defaultValueReturnsOne() {
        assertEquals(1, ConsecutiveFailureConfig.defaultValue());
    }

    @Test
    void getConsecutiveFailuresBeforeAlertOrDefaultReturnsDefaultWhenFieldNull() {
        var cfg = new ConsecutiveFailureConfig();  // consecutiveFailuresBeforeAlert = null
        assertEquals(1, cfg.getConsecutiveFailuresBeforeAlertOrDefault());
    }

    @Test
    void getConsecutiveFailuresBeforeAlertOrDefaultReturnsFieldWhenSet() {
        var cfg = new ConsecutiveFailureConfig();
        cfg.setConsecutiveFailuresBeforeAlert(3);
        assertEquals(3, cfg.getConsecutiveFailuresBeforeAlertOrDefault());
    }
}
