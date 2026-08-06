package dev.springbootstaticanalysis.analysis;

import java.util.UUID;

public record HttpEndpointView(
        UUID id,
        String httpMethod,
        String path,
        UUID controllerMethodId,
        String controller,
        String method,
        String signature,
        String sourcePath,
        int startLine,
        int endLine,
        String requestType,
        String responseType) {
}
