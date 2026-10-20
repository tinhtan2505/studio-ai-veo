package nqt.base_java_spring_be.veo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class VeoVideoService {
    private static final Pattern OPERATION_NAME = Pattern.compile(
            "models/[A-Za-z0-9._-]+/operations/[A-Za-z0-9._-]+"
    );
    private static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    @Value("${GEMINI_API_KEY:AIzaSyB6tyCUKMgY7AFTjQTElnug2kBjC-MqIs4}")
    private String apiKey;

    @Value("${GEMINI_API_BASE_URL:https://generativelanguage.googleapis.com/v1beta}")
    private String apiBaseUrl;

    public VeoVideoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public VeoVideoStartResponse start(
            String prompt,
            String shotId,
            String model,
            String aspectRatio,
            String resolution,
            int durationSeconds,
            MultipartFile image
    ) {
        requireApiKey();
        if (!StringUtils.hasText(prompt)) {
            throw new VeoProviderException(HttpStatus.BAD_REQUEST, "Prompt cảnh quay không được để trống.");
        }

        String normalizedModel = normalizeModel(model);
        validateOptions(normalizedModel, aspectRatio, resolution, durationSeconds, image);

        try {
            ObjectNode request = objectMapper.createObjectNode();
            ArrayNode instances = request.putArray("instances");
            ObjectNode instance = instances.addObject();
            instance.put("prompt", prompt.trim());

            boolean imageToVideo = image != null && !image.isEmpty();
            if (imageToVideo) {
                ObjectNode inlineData = instance.putObject("image").putObject("inlineData");
                inlineData.put("mimeType", image.getContentType());
                inlineData.put("data", Base64.getEncoder().encodeToString(image.getBytes()));
            }

            ObjectNode parameters = request.putObject("parameters");
            parameters.put("aspectRatio", aspectRatio);
            parameters.put("resolution", resolution);
            parameters.put("durationSeconds", Integer.toString(durationSeconds));
            parameters.put("personGeneration", imageToVideo ? "allow_adult" : "allow_all");

            JsonNode response = sendJson(
                    "POST",
                    "/models/" + normalizedModel + ":predictLongRunning",
                    request
            );
            String operationName = response.path("name").asText("");
            if (!OPERATION_NAME.matcher(operationName).matches()) {
                throw providerError(HttpStatus.BAD_GATEWAY, response, "Veo không trả về operation hợp lệ.");
            }

            UUID id = UUID.randomUUID();
            Job job = new Job(
                    id,
                    StringUtils.hasText(shotId) ? shotId.trim() : null,
                    operationName,
                    normalizedModel,
                    aspectRatio,
                    resolution,
                    durationSeconds,
                    imageToVideo
            );
            jobs.put(id, job);

            return new VeoVideoStartResponse(
                    id,
                    job.shotId,
                    job.status,
                    job.model,
                    job.aspectRatio,
                    job.resolution,
                    job.durationSeconds,
                    job.imageToVideo,
                    "/api/veo/videos/" + id,
                    "/api/veo/videos/" + id + "/download"
            );
        } catch (IOException e) {
            throw new VeoProviderException(HttpStatus.BAD_REQUEST, "Không đọc được Source Image: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VeoProviderException(HttpStatus.BAD_GATEWAY, "Yêu cầu Veo bị gián đoạn.");
        }
    }

    public VeoVideoStatusResponse status(UUID id) {
        Job job = findJob(id);
        refresh(job);
        return toStatus(job);
    }

    public DownloadedVideo download(UUID id) {
        Job job = findJob(id);
        refresh(job);
        if (!"SUCCEEDED".equals(job.status) || !StringUtils.hasText(job.videoUri)) {
            throw new VeoProviderException(
                    HttpStatus.CONFLICT,
                    "Video chưa sẵn sàng. Trạng thái hiện tại: " + job.status + "."
            );
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(job.videoUri))
                    .timeout(Duration.ofMinutes(5))
                    .header("x-goog-api-key", apiKey)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new VeoProviderException(
                        HttpStatus.BAD_GATEWAY,
                        "Không tải được file video từ Google (HTTP " + response.statusCode() + ")."
                );
            }
            return new DownloadedVideo(response.body(), job.fileName());
        } catch (VeoProviderException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VeoProviderException(HttpStatus.BAD_GATEWAY, "Tải video bị gián đoạn.");
        } catch (Exception e) {
            throw new VeoProviderException(HttpStatus.BAD_GATEWAY, "Không tải được video: " + e.getMessage());
        }
    }

    private void refresh(Job job) {
        if ("SUCCEEDED".equals(job.status) || "FAILED".equals(job.status)) {
            return;
        }

        try {
            JsonNode operation = sendJson("GET", "/" + job.operationName, null);
            job.updatedAt = Instant.now();
            if (!operation.path("done").asBoolean(false)) {
                job.status = "RUNNING";
                return;
            }

            JsonNode error = operation.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                job.status = "FAILED";
                job.error = error.path("message").asText("Veo từ chối yêu cầu.");
                return;
            }

            String videoUri = extractVideoUri(operation);
            if (!StringUtils.hasText(videoUri)) {
                job.status = "FAILED";
                job.error = "Veo hoàn tất nhưng không trả về URI video.";
                return;
            }
            job.videoUri = videoUri;
            job.status = "SUCCEEDED";
        } catch (VeoProviderException e) {
            job.updatedAt = Instant.now();
            job.status = "FAILED";
            job.error = e.getMessage();
        } catch (IOException e) {
            job.updatedAt = Instant.now();
            job.status = "FAILED";
            job.error = "Không đọc được phản hồi từ Google Veo: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.updatedAt = Instant.now();
            job.status = "FAILED";
            job.error = "Kiểm tra trạng thái Veo bị gián đoạn.";
        }
    }

    private JsonNode sendJson(String method, String path, JsonNode body) throws IOException, InterruptedException {
        URI uri = URI.create(trimTrailingSlash(apiBaseUrl) + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .header("x-goog-api-key", apiKey)
                .header("Accept", "application/json");

        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        } else {
            builder.GET();
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode payload = response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw providerError(HttpStatus.BAD_GATEWAY, payload, "Google Veo trả về HTTP " + response.statusCode() + ".");
        }
        return payload;
    }

    private String extractVideoUri(JsonNode operation) {
        String[] paths = {
                "/response/generateVideoResponse/generatedSamples/0/video/uri",
                "/response/generatedVideos/0/video/uri",
                "/response/generatedSamples/0/video/uri",
                "/response/videos/0/uri"
        };
        for (String path : paths) {
            String value = operation.at(path).asText("");
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private void validateOptions(String model, String aspectRatio, String resolution, int durationSeconds, MultipartFile image) {
        if (!("16:9".equals(aspectRatio) || "9:16".equals(aspectRatio))) {
            throw new VeoProviderException(HttpStatus.BAD_REQUEST, "aspectRatio chỉ được là 16:9 hoặc 9:16.");
        }
        if (!("720p".equals(resolution) || "1080p".equals(resolution) || "4k".equals(resolution))) {
            throw new VeoProviderException(HttpStatus.BAD_REQUEST, "resolution chỉ được là 720p, 1080p hoặc 4k.");
        }
        if (durationSeconds != 4 && durationSeconds != 6 && durationSeconds != 8) {
            throw new VeoProviderException(HttpStatus.BAD_REQUEST, "durationSeconds chỉ được là 4, 6 hoặc 8.");
        }
        if (("1080p".equals(resolution) || "4k".equals(resolution)) && durationSeconds != 8) {
            throw new VeoProviderException(HttpStatus.BAD_REQUEST, "1080p/4k yêu cầu durationSeconds=8.");
        }
        if (image != null && !image.isEmpty()) {
            String contentType = image.getContentType();
            if (!"image/png".equalsIgnoreCase(contentType) && !"image/jpeg".equalsIgnoreCase(contentType)) {
                throw new VeoProviderException(HttpStatus.BAD_REQUEST, "Source Image chỉ nhận PNG hoặc JPEG.");
            }
            if (image.getSize() > MAX_IMAGE_BYTES) {
                throw new VeoProviderException(HttpStatus.BAD_REQUEST, "Source Image không được vượt quá 20 MB.");
            }
        }
        if (!(model.startsWith("veo-3.1-") || model.startsWith("veo-3-"))) {
            throw new VeoProviderException(HttpStatus.BAD_REQUEST, "Model Veo không được hỗ trợ: " + model);
        }
    }

    private String normalizeModel(String model) {
        String value = StringUtils.hasText(model) ? model.trim() : "veo-3.1-generate-preview";
        return switch (value) {
            case "veo-3.1-generate-001" -> "veo-3.1-generate-preview";
            case "veo-3.1-fast-generate-001" -> "veo-3.1-fast-generate-preview";
            default -> value;
        };
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new VeoProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Backend chưa cấu hình GEMINI_API_KEY."
            );
        }
    }

    private Job findJob(UUID id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new VeoProviderException(HttpStatus.NOT_FOUND, "Không tìm thấy tác vụ Veo: " + id);
        }
        return job;
    }

    private VeoVideoStatusResponse toStatus(Job job) {
        return new VeoVideoStatusResponse(
                job.id,
                job.shotId,
                job.status,
                job.model,
                job.aspectRatio,
                job.resolution,
                job.durationSeconds,
                "SUCCEEDED".equals(job.status) || "FAILED".equals(job.status),
                "SUCCEEDED".equals(job.status) ? "/api/veo/videos/" + job.id + "/download" : null,
                job.error,
                job.updatedAt
        );
    }

    private VeoProviderException providerError(HttpStatus status, JsonNode payload, String fallback) {
        String message = payload.path("error").path("message").asText("");
        return new VeoProviderException(status, StringUtils.hasText(message) ? message : fallback);
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }

    public record DownloadedVideo(byte[] bytes, String fileName) {
    }

    private static final class Job {
        private final UUID id;
        private final String shotId;
        private final String operationName;
        private final String model;
        private final String aspectRatio;
        private final String resolution;
        private final int durationSeconds;
        private final boolean imageToVideo;
        private volatile String status = "QUEUED";
        private volatile String videoUri;
        private volatile String error;
        private volatile Instant updatedAt = Instant.now();

        private Job(UUID id, String shotId, String operationName, String model, String aspectRatio,
                    String resolution, int durationSeconds, boolean imageToVideo) {
            this.id = id;
            this.shotId = shotId;
            this.operationName = operationName;
            this.model = model;
            this.aspectRatio = aspectRatio;
            this.resolution = resolution;
            this.durationSeconds = durationSeconds;
            this.imageToVideo = imageToVideo;
        }

        private String fileName() {
            String safeShotId = StringUtils.hasText(shotId)
                    ? shotId.replaceAll("[^A-Za-z0-9._-]", "_")
                    : "veo-video";
            return safeShotId + "-" + id + ".mp4";
        }
    }
}
