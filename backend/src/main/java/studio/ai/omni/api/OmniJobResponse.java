package studio.ai.omni.api;

import java.time.Instant;
import java.util.UUID;

public record OmniJobResponse(
        UUID id,
        String shotId,
        OmniMode mode,
        OmniJobStatus status,
        boolean done,
        String interactionId,
        String previousInteractionId,
        OmniTask task,
        String aspectRatio,
        String resolution,
        boolean stored,
        String videoUrl,
        Double durationSeconds,
        String errorCode,
        String providerStatus,
        String error,
        boolean retryable,
        Instant createdAt,
        Instant updatedAt,
        String statusUrl
) {
}
