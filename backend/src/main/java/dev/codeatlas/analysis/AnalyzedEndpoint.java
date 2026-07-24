package dev.codeatlas.analysis;

import java.util.UUID;

public record AnalyzedEndpoint(
        UUID id,
        UUID controllerMethodId,
        String httpMethod,
        String path,
        String requestType,
        String responseType) {
}
