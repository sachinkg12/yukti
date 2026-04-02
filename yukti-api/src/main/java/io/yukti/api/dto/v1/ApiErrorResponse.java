package io.yukti.api.dto.v1;

import java.util.List;

/**
 * API error response schema.
 */
public record ApiErrorResponse(
    String requestId,
    String errorCode,
    String message,
    List<DetailDto> details
) {
    public record DetailDto(String field, String issue) {}
}
