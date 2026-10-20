package nqt.base_java_spring_be.veo;

import java.time.Instant;
import java.util.UUID;

public record VeoVideoStatusResponse(
        UUID id,
        String shotId,
        String status,
        String model,
        String aspectRatio,
        String resolution,
        int durationSeconds,
        boolean done,
        String videoUrl,
        String error,
        Instant updatedAt
) {
}
