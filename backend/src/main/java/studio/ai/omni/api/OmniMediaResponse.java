package studio.ai.omni.api;

import java.time.Instant;

public record OmniMediaResponse(
        String fileId,
        String name,
        String displayName,
        String uri,
        String mimeType,
        long sizeBytes,
        String state,
        Double durationSeconds,
        Instant expirationTime,
        String error
) {
}
