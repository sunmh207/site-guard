package com.siteguard.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新 Token 请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {
    private String token;
}

