package studio.ai.omni.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import studio.ai.omni.api.OmniJobRequest;
import studio.ai.omni.api.OmniMediaInput;
import studio.ai.omni.config.OmniProperties;
import studio.ai.omni.error.OmniApiException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Flow;

@Component
public class GeminiOmniClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final OmniProperties properties;

    public GeminiOmniClient(ObjectMapper objectMapper, HttpClient omniHttpClient, OmniProperties properties) {
        this.objectMapper = objectMapper;
        this.httpClient = omniHttpClient;
        this.properties = properties;
    }

    public boolean configured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    public FileInfo upload(MultipartFile multipartFile) {
        requireConfigured();
        String mimeType = Objects.requireNonNullElse(multipartFile.getContentType(), "application/octet-stream");
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.putObject("file").put("display_name", safeDisplayName(multipartFile.getOriginalFilename()));

        try {
            HttpRequest startRequest = baseRequest(uploadUri("/upload/v1beta/files"))
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Header-Content-Length", Long.toString(multipartFile.getSize()))
                    .header("X-Goog-Upload-Header-Content-Type", mimeType)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(metadata)))
                    .build();
            HttpResponse<String> startResponse = send(startRequest, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(startResponse.statusCode(), startResponse.body(), "MEDIA_UPLOAD_START_FAILED");
            String uploadUrl = startResponse.headers().firstValue("x-goog-upload-url")
                    .orElseThrow(() -> new OmniApiException(
                            HttpStatus.BAD_GATEWAY,
                            "MEDIA_UPLOAD_START_FAILED",
                            "Gemini Files API không trả về URL tải lên.",
                            null,
                            true
                    ));

            HttpRequest.BodyPublisher streamPublisher = HttpRequest.BodyPublishers.ofInputStream(() -> {
                try {
                    return multipartFile.getInputStream();
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
            Flow.Publisher<ByteBuffer> publisher = streamPublisher;
            HttpRequest.BodyPublisher sizedPublisher = HttpRequest.BodyPublishers.fromPublisher(
                    publisher, multipartFile.getSize()
            );
            HttpRequest uploadRequest = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(requestTimeout())
                    .header("X-Goog-Upload-Offset", "0")
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .POST(sizedPublisher)
                    .build();
            HttpResponse<String> uploadResponse = send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(uploadResponse.statusCode(), uploadResponse.body(), "MEDIA_UPLOAD_FAILED");
            return parseFile(readJson(uploadResponse.body()).path("file"));
        } catch (UncheckedIOException exception) {
            throw new OmniApiException(
                    HttpStatus.BAD_REQUEST, "MEDIA_READ_FAILED", "Không đọc được tệp tải lên.", null, false
            );
        } catch (IOException exception) {
            throw transport("MEDIA_UPLOAD_FAILED", exception);
        }
    }

    public FileInfo getFile(String fileId) {
        requireConfigured();
        String normalized = normalizeFileId(fileId);
        HttpRequest request = baseRequest(apiUri("/v1beta/files/" + normalized)).GET().build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.statusCode() == 410) {
            throw new OmniApiException(
                    HttpStatus.GONE,
                    "MEDIA_EXPIRED",
                    "Media không còn tồn tại trên Gemini Files API; hãy tải lại tệp.",
                    "NOT_FOUND",
                    false
            );
        }
        ensureSuccess(response.statusCode(), response.body(), "MEDIA_LOOKUP_FAILED");
        return parseFile(readJson(response.body()));
    }

    public void deleteFile(String fileId) {
        requireConfigured();
        String normalized = normalizeFileId(fileId);
        HttpRequest request = baseRequest(apiUri("/v1beta/files/" + normalized)).DELETE().build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 404) {
            ensureSuccess(response.statusCode(), response.body(), "MEDIA_DELETE_FAILED");
        }
    }

    public InteractionResult createInteraction(OmniJobRequest jobRequest, List<ResolvedMedia> media) {
        requireConfigured();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", OmniProperties.MODEL);
        ArrayNode input = request.putArray("input");
        for (ResolvedMedia item : media) {
            ObjectNode content = input.addObject();
            content.put("type", item.file().mimeType().startsWith("image/") ? "image" : "video");
            content.put("uri", item.file().uri());
            content.put("mime_type", item.file().mimeType());
        }
        input.addObject().put("type", "text").put("text", jobRequest.prompt().trim());
        if (StringUtils.hasText(jobRequest.previousInteractionId())) {
            request.put("previous_interaction_id", jobRequest.previousInteractionId().trim());
        }
        ObjectNode responseFormat = request.putObject("response_format");
        responseFormat.put("type", "video");
        responseFormat.put("aspect_ratio", jobRequest.aspectRatio());
        responseFormat.put("resolution", jobRequest.resolution());
        responseFormat.put("delivery", "uri");
        request.put("background", false);
        request.put("stream", false);
        request.put("store", jobRequest.shouldStore());
        if (jobRequest.task() != null) {
            request.putObject("generation_config")
                    .putObject("video_config")
                    .put("task", jobRequest.task().toJson());
        }

        try {
            HttpRequest httpRequest = baseRequest(apiUri("/v1beta/interactions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = send(httpRequest, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body(), "INTERACTION_FAILED");
            JsonNode payload = readJson(response.body());
            String id = payload.path("id").asText("");
            if (!StringUtils.hasText(id)) {
                throw new OmniApiException(
                        HttpStatus.BAD_GATEWAY,
                        "INVALID_INTERACTION_RESPONSE",
                        "Gemini Omni không trả về interaction ID.",
                        null,
                        true
                );
            }
            String interactionStatus = payload.path("status").asText("completed");
            if ("failed".equalsIgnoreCase(interactionStatus)) {
                String failureText = payload.path("error").toString().toUpperCase(Locale.ROOT);
                boolean safety = failureText.contains("SAFETY") || failureText.contains("BLOCKED")
                        || failureText.contains("PROHIBITED_CONTENT");
                throw new OmniApiException(
                        HttpStatus.BAD_REQUEST,
                        safety ? "SAFETY_BLOCKED" : "INTERACTION_FAILED",
                        safety
                                ? "Gemini đã chặn yêu cầu theo chính sách an toàn. Hãy điều chỉnh nội dung hoặc media."
                                : "Gemini không thể hoàn tất interaction.",
                        interactionStatus,
                        !safety
                );
            }
            VideoOutput video = extractVideo(payload);
            return new InteractionResult(id, interactionStatus, video);
        } catch (IOException exception) {
            throw transport("INTERACTION_FAILED", exception);
        }
    }

    public void deleteInteraction(String interactionId) {
        requireConfigured();
        String safeId = normalizeInteractionId(interactionId);
        HttpRequest request = baseRequest(apiUri("/v1beta/interactions/" + safeId)).DELETE().build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 404) {
            ensureSuccess(response.statusCode(), response.body(), "INTERACTION_DELETE_FAILED");
        }
    }

    public void download(String downloadUri, Path destination) {
        requireConfigured();
        if (!StringUtils.hasText(downloadUri)) {
            throw new OmniApiException(
                    HttpStatus.BAD_GATEWAY, "OUTPUT_DOWNLOAD_UNAVAILABLE",
                    "Gemini Files API chưa cung cấp download URI.", null, true
            );
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUri))
                .timeout(requestTimeout())
                .header("x-goog-api-key", properties.getApiKey())
                .header("Accept", "video/mp4,application/octet-stream")
                .GET()
                .build();
        OpenOption[] options = {
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        };
        HttpResponse<Path> response = send(request, HttpResponse.BodyHandlers.ofFile(destination, options));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OmniApiException(
                    mapStatus(response.statusCode()),
                    "OUTPUT_DOWNLOAD_FAILED",
                    "Không tải được video đã tạo từ Gemini (HTTP " + response.statusCode() + ").",
                    null,
                    isRetryable(response.statusCode())
            );
        }
    }

    public byte[] decodeInlineVideo(VideoOutput output) {
        try {
            return Base64.getDecoder().decode(output.data());
        } catch (IllegalArgumentException exception) {
            throw new OmniApiException(
                    HttpStatus.BAD_GATEWAY,
                    "INVALID_VIDEO_DATA",
                    "Gemini Omni trả về dữ liệu video không hợp lệ.",
                    null,
                    false
            );
        }
    }

    public static String fileIdFromUri(String uri) {
        if (!StringUtils.hasText(uri)) return "";
        int marker = uri.lastIndexOf("/files/");
        String value = marker >= 0 ? uri.substring(marker + 7) : uri.substring(uri.lastIndexOf('/') + 1);
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int action = value.indexOf(':');
        if (action >= 0) value = value.substring(0, action);
        return value.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private VideoOutput extractVideo(JsonNode payload) {
        List<JsonNode> candidates = new ArrayList<>();
        JsonNode steps = payload.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                if (!"model_output".equals(step.path("type").asText())) continue;
                JsonNode content = step.path("content");
                if (content.isArray()) content.forEach(candidates::add);
                if (content.isObject()) {
                    candidates.add(content);
                    JsonNode parts = content.path("parts");
                    if (parts.isArray()) parts.forEach(candidates::add);
                }
            }
        }
        JsonNode direct = payload.path("output_video");
        if (direct.isObject()) candidates.add(direct);
        for (JsonNode candidate : candidates) {
            String type = candidate.path("type").asText("video");
            if (!"video".equals(type) && !"output_video".equals(type)) continue;
            String uri = firstText(
                    candidate.path("uri"),
                    candidate.path("file_uri"),
                    candidate.path("fileData").path("fileUri"),
                    candidate.path("file_data").path("file_uri")
            );
            String data = firstText(
                    candidate.path("data"),
                    candidate.path("inlineData").path("data"),
                    candidate.path("inline_data").path("data")
            );
            if (StringUtils.hasText(uri) || StringUtils.hasText(data)) {
                return new VideoOutput(
                        uri,
                        data,
                        firstTextOrDefault("video/mp4",
                                candidate.path("mime_type"),
                                candidate.path("mimeType"),
                                candidate.path("inlineData").path("mimeType"),
                                candidate.path("inline_data").path("mime_type")
                        )
                );
            }
        }
        String payloadText = payload.toString().toUpperCase(Locale.ROOT);
        if (payloadText.contains("SAFETY") || payloadText.contains("PROHIBITED_CONTENT")
                || payloadText.contains("BLOCKED_REASON")) {
            throw new OmniApiException(
                    HttpStatus.BAD_REQUEST,
                    "SAFETY_BLOCKED",
                    "Gemini đã chặn yêu cầu theo chính sách an toàn. Hãy điều chỉnh nội dung hoặc media.",
                    "SAFETY",
                    false
            );
        }
        throw new OmniApiException(
                HttpStatus.BAD_GATEWAY,
                "VIDEO_OUTPUT_MISSING",
                "Gemini Omni hoàn tất nhưng không trả về video.",
                null,
                false
        );
    }

    private FileInfo parseFile(JsonNode file) {
        if (!file.isObject()) {
            throw new OmniApiException(
                    HttpStatus.BAD_GATEWAY, "INVALID_FILE_RESPONSE",
                    "Gemini Files API trả về metadata không hợp lệ.", null, true
            );
        }
        String name = file.path("name").asText("");
        String id = name.startsWith("files/") ? name.substring(6) : name;
        return new FileInfo(
                id,
                name,
                file.path("displayName").asText(file.path("display_name").asText("media")),
                file.path("uri").asText(""),
                file.path("downloadUri").asText(file.path("download_uri").asText("")),
                file.path("mimeType").asText(file.path("mime_type").asText("application/octet-stream")),
                parseLong(file.path("sizeBytes").asText(file.path("size_bytes").asText("0"))),
                file.path("state").asText("STATE_UNSPECIFIED").toUpperCase(Locale.ROOT),
                parseDuration(file.path("videoMetadata").path("videoDuration").asText(
                        file.path("video_metadata").path("video_duration").asText("")
                )),
                parseInstant(file.path("expirationTime").asText(file.path("expiration_time").asText(""))),
                file.path("error").path("message").asText("")
        );
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OmniApiException(
                    HttpStatus.BAD_GATEWAY, "UPSTREAM_INTERRUPTED",
                    "Kết nối tới Gemini bị gián đoạn.", null, true
            );
        } catch (IOException exception) {
            throw transport("UPSTREAM_UNAVAILABLE", exception);
        }
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(requestTimeout())
                .header("x-goog-api-key", properties.getApiKey())
                .header("Accept", "application/json");
    }

    private URI apiUri(String path) {
        return URI.create(trimBaseUrl() + path);
    }

    private URI uploadUri(String path) {
        return URI.create(trimBaseUrl() + path);
    }

    private String trimBaseUrl() {
        return properties.getBaseUrl().replaceFirst("/+$", "");
    }

    private Duration requestTimeout() {
        return Duration.ofMinutes(Math.max(1, properties.getRequestTimeoutMinutes()));
    }

    private void ensureSuccess(int statusCode, String body, String fallbackCode) {
        if (statusCode >= 200 && statusCode < 300) return;
        JsonNode error = safeReadJson(body).path("error");
        String providerStatus = error.path("status").asText("");
        String rawMessage = error.path("message").asText("");
        String errorText = (providerStatus + " " + rawMessage + " " + body).toUpperCase(Locale.ROOT);
        boolean safety = errorText.contains("SAFETY") || errorText.contains("BLOCKED")
                || errorText.contains("PROHIBITED_CONTENT");
        String code = safety ? "SAFETY_BLOCKED"
                : statusCode == 429 ? "RATE_LIMITED"
                : StringUtils.hasText(providerStatus) ? providerStatus : fallbackCode;
        String message = safety
                ? "Gemini đã chặn yêu cầu theo chính sách an toàn. Hãy điều chỉnh nội dung hoặc media."
                : statusCode == 429
                ? "Gemini đang giới hạn tần suất. Hãy thử lại sau."
                : statusCode >= 500
                ? "Gemini tạm thời không xử lý được yêu cầu (HTTP " + statusCode + ")."
                : "Gemini từ chối yêu cầu (HTTP " + statusCode + ", " + code + ").";
        throw new OmniApiException(
                mapStatus(statusCode),
                code,
                message,
                StringUtils.hasText(providerStatus) ? providerStatus : null,
                isRetryable(statusCode)
        );
    }

    private HttpStatus mapStatus(int statusCode) {
        if (statusCode == 400 || statusCode == 422) return HttpStatus.BAD_REQUEST;
        if (statusCode == 404) return HttpStatus.NOT_FOUND;
        if (statusCode == 409) return HttpStatus.CONFLICT;
        if (statusCode == 413) return HttpStatus.PAYLOAD_TOO_LARGE;
        if (statusCode == 429) return HttpStatus.TOO_MANY_REQUESTS;
        if (statusCode == 401 || statusCode == 403) return HttpStatus.BAD_GATEWAY;
        return HttpStatus.BAD_GATEWAY;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException exception) {
            throw new OmniApiException(
                    HttpStatus.BAD_GATEWAY, "INVALID_UPSTREAM_RESPONSE",
                    "Gemini API trả về JSON không hợp lệ.", null, true
            );
        }
    }

    private JsonNode safeReadJson(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new OmniApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GEMINI_API_KEY_MISSING",
                    "Backend chưa được cấu hình GEMINI_API_KEY.",
                    null,
                    false
            );
        }
    }

    private OmniApiException transport(String code, IOException exception) {
        return new OmniApiException(
                HttpStatus.BAD_GATEWAY,
                code,
                "Không kết nối được Gemini API: " + exception.getClass().getSimpleName() + ".",
                null,
                true
        );
    }

    private String normalizeFileId(String fileId) {
        String value = Objects.requireNonNullElse(fileId, "").replaceFirst("^files/", "");
        if (!value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new OmniApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_ID", "File ID không hợp lệ.");
        }
        return value;
    }

    private String normalizeInteractionId(String interactionId) {
        String value = Objects.requireNonNullElse(interactionId, "").trim();
        if (!value.matches("[A-Za-z0-9._-]{1,200}")) {
            throw new OmniApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_INTERACTION_ID", "Interaction ID không hợp lệ."
            );
        }
        return value;
    }

    private String safeDisplayName(String value) {
        String fileName = StringUtils.hasText(value) ? value : "media";
        String cleaned = fileName.replaceAll("[\\p{Cntrl}]", "");
        return cleaned.substring(0, Math.min(200, cleaned.length()));
    }

    private long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return 0; }
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = node.asText("");
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private String firstTextOrDefault(String fallback, JsonNode... nodes) {
        String value = firstText(nodes);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Double parseDuration(String value) {
        if (!StringUtils.hasText(value) || !value.endsWith("s")) return null;
        try { return Double.parseDouble(value.substring(0, value.length() - 1)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) { return null; }
    }

    public record FileInfo(
            String id,
            String name,
            String displayName,
            String uri,
            String downloadUri,
            String mimeType,
            long sizeBytes,
            String state,
            Double durationSeconds,
            Instant expirationTime,
            String error
    ) {
    }

    public record ResolvedMedia(OmniMediaInput input, FileInfo file) {
    }

    public record VideoOutput(String uri, String data, String mimeType) {
        public boolean inline() { return StringUtils.hasText(data); }
    }

    public record InteractionResult(String id, String status, VideoOutput video) {
    }
}
