package studio.ai.omni.api;

import java.util.List;

public record OmniConfigResponse(
        boolean configured,
        String model,
        long maxUploadBytes,
        int maxConcurrentJobs,
        List<String> aspectRatios,
        List<String> resolutions,
        List<String> tasks,
        List<String> imageMimeTypes,
        List<String> videoMimeTypes
) {
}
