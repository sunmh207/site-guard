package com.siteguard.monitor.probe;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PathCheckTypeTest {

    @Test
    void name_returnsUpperUnderscoreConstant() {
        assertEquals("HTTP_STATUS", PathCheckType.HTTP_STATUS.name());
        assertEquals("KEYWORD", PathCheckType.KEYWORD.name());
    }

    @Test
    void valueOf_roundTrips() {
        assertEquals(PathCheckType.HTTP_STATUS, PathCheckType.valueOf("HTTP_STATUS"));
        assertEquals(PathCheckType.KEYWORD, PathCheckType.valueOf("KEYWORD"));
    }
}
