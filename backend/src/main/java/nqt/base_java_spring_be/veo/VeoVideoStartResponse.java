package nqt.base_java_spring_be.veo;

import java.util.UUID;

public record VeoVideoStartResponse(
        UUID id,
        String shotId,
        String status,
        String model,
        String aspectRatio,
        String resolution,
        int durationSeconds,
        boolean imageToVideo,
        String statusUrl,
        String downloadUrl
) {
}
