package com.siteguard.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IdPayload {
    @NotNull
    @Schema(description = "ID", type = "string", requiredMode = Schema.RequiredMode.REQUIRED, example = "12345")
    private Long id;
}
