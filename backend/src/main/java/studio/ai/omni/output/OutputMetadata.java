package studio.ai.omni.output;

import studio.ai.omni.api.OmniMode;
import studio.ai.omni.api.OmniTask;

import java.time.Instant;

public record OutputMetadata(
        String interactionId,
        String shotId,
        OmniMode mode,
        String previousInteractionId,
        OmniTask task,
        String aspectRatio,
        String resolution,
        boolean stored,
        String mimeType,
        long sizeBytes,
        Double durationSeconds,
        Instant createdAt
) {
}
