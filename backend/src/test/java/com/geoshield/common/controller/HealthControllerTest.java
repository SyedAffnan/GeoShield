package com.geoshield.common.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.common.api.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HealthControllerTest {

    @Test
    void healthReturnsOkWithStatusUp() {
        HealthController controller = new HealthController();
        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertEquals("GeoShield backend operational", response.getBody().message());
        assertEquals("UP", response.getBody().data().get("status"));
        assertNotNull(response.getBody().timestamp());
    }
}
