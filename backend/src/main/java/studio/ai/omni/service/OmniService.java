package studio.ai.omni.service;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import studio.ai.omni.api.MediaRole;
import studio.ai.omni.api.OmniConfigResponse;
import studio.ai.omni.api.OmniJobRequest;
import studio.ai.omni.api.OmniJobResponse;
import studio.ai.omni.api.OmniJobStatus;
import studio.ai.omni.api.OmniMediaInput;
import studio.ai.omni.api.OmniMediaResponse;
import studio.ai.omni.api.OmniMode;
import studio.ai.omni.api.OmniTask;
import studio.ai.omni.client.GeminiOmniClient;
import studio.ai.omni.client.GeminiOmniClient.FileInfo;
import studio.ai.omni.client.GeminiOmniClient.InteractionResult;
import studio.ai.omni.client.GeminiOmniClient.ResolvedMedia;
import studio.ai.omni.client.GeminiOmniClient.VideoOutput;
import studio.ai.omni.config.OmniProperties;
import studio.ai.omni.error.OmniApiException;
import studio.ai.omni.output.OutputStore;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
public class OmniService {
    public static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/heic", "image/heif",
            "image/gif", "image/bmp", "image/tiff"
    );
    public static final Set<String> VIDEO_MIME_TYPES = Set.of(
            "video/mp4", "video/mpeg", "video/mpg", "video/mov", "video/quicktime",
            "video/avi", "video/x-flv", "video/webm", "video/wmv", "video/3gpp"
    );

    private final GeminiOmniClient client;
    private final OmniProperties properties;
    private final OutputStore outputStore;
    private final ExecutorService executor;
    private final Map<UUID, MutableJob> jobs = new ConcurrentHashMap<>();

    public OmniService(GeminiOmniClient client, OmniProperties properties,
                       OutputStore outputStore, ExecutorService omniJobExecutor) {
        this.client = client;
        this.properties = properties;
        this.outputStore = outputStore;
        this.executor = omniJobExecutor;
    }

    public OmniConfigResponse config() {
        return new OmniConfigResponse(
                client.configured(),
                OmniProperties.MODEL,
                properties.getMaxUploadBytes(),
                properties.getMaxConcurrentJobs(),
                List.of("16:9", "9:16"),
                List.of("360p", "720p", "1080p", "4k"),
                List.of("auto", "text_to_video", "image_to_video", "reference_to_video", "edit", "extend"),
                IMAGE_MIME_TYPES.stream().sorted().toList(),
                VIDEO_MIME_TYPES.stream().sorted().toList()
        );
    }

    public OmniMediaResponse upload(MultipartFile file) {
        validateUpload(file);
        return toMediaResponse(client.upload(file));
    }

    public OmniMediaResponse media(String fileId) {
        return toMediaResponse(client.getFile(fileId));
    }

    public void deleteMedia(String fileId) {
        client.deleteFile(fileId);
    }

    public OmniJobResponse start(OmniJobRequest request) {
        validateRequestStructure(request);
        UUID id = UUID.randomUUID();
        MutableJob job = new MutableJob(id, request);
        jobs.put(id, job);
        OmniJobResponse accepted = job.response();
        executor.submit(() -> execute(job));
        return accepted;
    }

    public OmniJobResponse job(UUID id) {
        MutableJob job = jobs.get(id);
        if (job == null) {
            throw new OmniApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Không tìm thấy Omni job: " + id + ".");
        }
        return job.response();
    }

    public OutputStore.StoredOutput output(String interactionId) {
        return outputStore.require(interactionId);
    }

    public void deleteOutput(String interactionId) {
        outputStore.delete(interactionId);
    }

    public void deleteInteraction(String interactionId) {
        try {
            client.deleteInteraction(interactionId);
        } finally {
            outputStore.delete(interactionId);
        }
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void cleanExpiredJobs() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(Math.max(1, properties.getJobRetentionHours())));
        jobs.entrySet().removeIf(entry -> entry.getValue().done() && entry.getValue().updatedAt.isBefore(cutoff));
    }

    private void execute(MutableJob job) {
        Path temp = null;
        try {
            job.status(OmniJobStatus.GENERATING);
            List<ResolvedMedia> media = resolveAndValidate(job.request);
            InteractionResult result = client.createInteraction(job.request, media);
            job.interactionId = result.id();
            VideoOutput video = result.video();
            Double duration = null;
            if (video.inline()) {
                job.status(OmniJobStatus.DOWNLOADING);
                outputStore.saveInline(
                        result.id(), job.request, client.decodeInlineVideo(video), video.mimeType(), null
                );
            } else {
                job.status(OmniJobStatus.PROCESSING_OUTPUT);
                String fileId = GeminiOmniClient.fileIdFromUri(video.uri());
                if (!StringUtils.hasText(fileId)) {
                    throw new OmniApiException(
                            HttpStatus.BAD_GATEWAY, "INVALID_OUTPUT_URI", "Gemini Omni trả về output URI không hợp lệ."
                    );
                }
                FileInfo generatedFile = waitForActiveFile(fileId);
                duration = generatedFile.durationSeconds();
                job.status(OmniJobStatus.DOWNLOADING);
                temp = outputStore.createTempFile(result.id());
                String downloadUri = StringUtils.hasText(generatedFile.downloadUri())
                        ? generatedFile.downloadUri()
                        : StringUtils.hasText(video.uri()) ? video.uri() : generatedFile.uri();
                client.download(downloadUri, temp);
                outputStore.commit(result.id(), job.request, temp, video.mimeType(), duration);
                temp = null;
            }
            job.durationSeconds = duration;
            job.status(OmniJobStatus.SUCCEEDED);
        } catch (OmniApiException exception) {
            job.fail(
                    exception.getCode(), exception.getProviderStatus(),
                    exception.getMessage(), exception.isRetryable()
            );
        } catch (Exception exception) {
            job.fail("INTERNAL_JOB_ERROR", null, "Không thể hoàn tất Omni job.", true);
        } finally {
            if (temp != null) {
                try { java.nio.file.Files.deleteIfExists(temp); } catch (Exception ignored) { }
            }
        }
    }

    private FileInfo waitForActiveFile(String fileId) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(
                Math.max(1, properties.getOutputPollTimeoutMinutes())
        ));
        while (Instant.now().isBefore(deadline)) {
            FileInfo file = client.getFile(fileId);
            if ("ACTIVE".equals(file.state())) return file;
            if ("FAILED".equals(file.state())) {
                throw new OmniApiException(
                        HttpStatus.BAD_GATEWAY,
                        "OUTPUT_PROCESSING_FAILED",
                        "Gemini không xử lý được output video.",
                        "FAILED",
                        false
                );
            }
            try {
                Thread.sleep(Duration.ofSeconds(Math.max(1, properties.getOutputPollSeconds())));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new OmniApiException(
                        HttpStatus.BAD_GATEWAY, "OUTPUT_POLL_INTERRUPTED",
                        "Theo dõi output Gemini bị gián đoạn.", null, true
                );
            }
        }
        throw new OmniApiException(
                HttpStatus.GATEWAY_TIMEOUT, "OUTPUT_POLL_TIMEOUT",
                "Quá thời gian chờ Gemini chuẩn bị output video.", null, true
        );
    }

    private List<ResolvedMedia> resolveAndValidate(OmniJobRequest request) {
        return validate(request, true);
    }

    private void validateRequestStructure(OmniJobRequest request) {
        validate(request, false);
    }

    private List<ResolvedMedia> validate(OmniJobRequest request, boolean resolveProviderMedia) {
        validateTaskForMode(request.mode(), request.task());
        List<OmniMediaInput> inputs = request.safeMedia();
        long firstFrames = count(inputs, MediaRole.FIRST_FRAME);
        long lastFrames = count(inputs, MediaRole.LAST_FRAME);
        long sourceVideos = count(inputs, MediaRole.SOURCE_VIDEO);
        long videoReferences = count(inputs, MediaRole.VIDEO_REFERENCE);

        long uniqueFileIds = inputs.stream().map(OmniMediaInput::fileId).distinct().count();
        if (uniqueFileIds != inputs.size()) {
            throw invalid("Mỗi Files API file chỉ được xuất hiện một lần trong danh sách media.");
        }
        long explicitOrders = inputs.stream().filter(input -> input.order() != null).count();
        long uniqueOrders = inputs.stream().filter(input -> input.order() != null)
                .map(OmniMediaInput::order).distinct().count();
        if (explicitOrders != uniqueOrders) {
            throw invalid("Thứ tự media không được trùng nhau.");
        }

        if (firstFrames > 1 || lastFrames > 1) {
            throw invalid("Chỉ được dùng tối đa một FIRST_FRAME và một LAST_FRAME.");
        }
        if (lastFrames == 1 && firstFrames == 0) {
            throw invalid("LAST_FRAME chỉ hợp lệ khi có FIRST_FRAME.");
        }
        if (sourceVideos > 1) {
            throw invalid("Chỉ được dùng một SOURCE_VIDEO.");
        }
        if (videoReferences > 3) {
            throw invalid("Chỉ được dùng tối đa ba VIDEO_REFERENCE.");
        }

        boolean hasPrevious = StringUtils.hasText(request.previousInteractionId());
        if (hasPrevious && !request.previousInteractionId().matches("[A-Za-z0-9._-]{1,200}")) {
            throw invalid("previousInteractionId không hợp lệ.");
        }
        if (request.mode() == OmniMode.GENERATE) {
            if (hasPrevious || sourceVideos > 0) {
                throw invalid("Chế độ GENERATE không nhận previous interaction hoặc SOURCE_VIDEO.");
            }
        } else if (hasPrevious == (sourceVideos == 1)) {
            throw invalid("EDIT/EXTEND phải dùng đúng một nguồn: previous interaction hoặc SOURCE_VIDEO.");
        }

        if (request.mode() == OmniMode.EXTEND && hasPrevious && outputStore.exists(request.previousInteractionId())) {
            Double duration = outputStore.require(request.previousInteractionId()).metadata().durationSeconds();
            if (duration != null && duration >= 40) {
                throw invalid("Chuỗi video đã đạt giới hạn mở rộng 40 giây.");
            }
        }

        if (!resolveProviderMedia) return List.of();

        List<ResolvedMedia> resolved = new ArrayList<>();
        for (OmniMediaInput input : inputs) {
            FileInfo file = client.getFile(input.fileId());
            if (!"ACTIVE".equals(file.state())) {
                throw new OmniApiException(
                        HttpStatus.CONFLICT,
                        "MEDIA_NOT_READY",
                        "Media " + input.fileId() + " chưa sẵn sàng (" + file.state() + ").",
                        file.state(),
                        "PROCESSING".equals(file.state())
                );
            }
            validateRole(input.role(), file);
            resolved.add(new ResolvedMedia(input, file));
        }
        return List.copyOf(resolved);
    }

    private void validateTaskForMode(OmniMode mode, OmniTask task) {
        if (task == null) return;
        Set<OmniTask> allowed = switch (mode) {
            case GENERATE -> EnumSet.of(
                    OmniTask.TEXT_TO_VIDEO, OmniTask.IMAGE_TO_VIDEO, OmniTask.REFERENCE_TO_VIDEO
            );
            case EDIT -> EnumSet.of(OmniTask.EDIT);
            case EXTEND -> EnumSet.of(OmniTask.EXTEND);
        };
        if (!allowed.contains(task)) {
            throw invalid("Task " + task.toJson() + " không tương thích với mode " + mode + ".");
        }
    }

    private void validateRole(MediaRole role, FileInfo file) {
        boolean image = IMAGE_MIME_TYPES.contains(file.mimeType());
        boolean video = VIDEO_MIME_TYPES.contains(file.mimeType());
        if ((role == MediaRole.FIRST_FRAME || role == MediaRole.LAST_FRAME || role == MediaRole.IMAGE_REFERENCE)
                && !image) {
            throw invalid(role + " yêu cầu tệp ảnh được hỗ trợ.");
        }
        if ((role == MediaRole.SOURCE_VIDEO || role == MediaRole.VIDEO_REFERENCE) && !video) {
            throw invalid(role + " yêu cầu tệp video được hỗ trợ.");
        }
        if ((role == MediaRole.SOURCE_VIDEO || role == MediaRole.VIDEO_REFERENCE)
                && file.durationSeconds() == null) {
            throw invalid(role + " thiếu videoDuration nên chưa thể xác thực giới hạn thời lượng.");
        }
        if (role == MediaRole.SOURCE_VIDEO && file.durationSeconds() > 10.0) {
            throw invalid("SOURCE_VIDEO để edit/extend không được dài quá 10 giây.");
        }
        if (role == MediaRole.VIDEO_REFERENCE && file.durationSeconds() > 3.0) {
            throw invalid("VIDEO_REFERENCE không được dài quá 3 giây.");
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Tệp media không được để trống.");
        }
        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new OmniApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "MEDIA_TOO_LARGE",
                    "Tệp media vượt quá giới hạn " + properties.getMaxUploadBytes() + " byte."
            );
        }
        String mimeType = file.getContentType();
        if (!IMAGE_MIME_TYPES.contains(mimeType) && !VIDEO_MIME_TYPES.contains(mimeType)) {
            throw new OmniApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_MEDIA_TYPE",
                    "Omni Studio chỉ nhận ảnh hoặc video thuộc định dạng Gemini hỗ trợ; audio reference không được hỗ trợ."
            );
        }
    }

    private OmniMediaResponse toMediaResponse(FileInfo file) {
        return new OmniMediaResponse(
                file.id(), file.name(), file.displayName(), file.uri(), file.mimeType(), file.sizeBytes(),
                file.state(), file.durationSeconds(), file.expirationTime(), file.error()
        );
    }

    private long count(List<OmniMediaInput> inputs, MediaRole role) {
        return inputs.stream().filter(input -> input.role() == role).count();
    }

    private OmniApiException invalid(String message) {
        return new OmniApiException(HttpStatus.BAD_REQUEST, "INVALID_OMNI_REQUEST", message);
    }

    private final class MutableJob {
        private final UUID id;
        private final OmniJobRequest request;
        private final Instant createdAt = Instant.now();
        private volatile Instant updatedAt = createdAt;
        private volatile OmniJobStatus status = OmniJobStatus.QUEUED;
        private volatile String interactionId;
        private volatile Double durationSeconds;
        private volatile String errorCode;
        private volatile String providerStatus;
        private volatile String error;
        private volatile boolean retryable;

        private MutableJob(UUID id, OmniJobRequest request) {
            this.id = id;
            this.request = request;
        }

        private void status(OmniJobStatus next) {
            this.status = next;
            this.updatedAt = Instant.now();
        }

        private void fail(String code, String upstreamStatus, String message, boolean canRetry) {
            this.errorCode = code;
            this.providerStatus = upstreamStatus;
            this.error = message;
            this.retryable = canRetry;
            status(OmniJobStatus.FAILED);
        }

        private boolean done() {
            return status == OmniJobStatus.SUCCEEDED || status == OmniJobStatus.FAILED;
        }

        private OmniJobResponse response() {
            return new OmniJobResponse(
                    id,
                    request.shotId(),
                    request.mode(),
                    status,
                    done(),
                    interactionId,
                    request.previousInteractionId(),
                    request.task(),
                    request.aspectRatio(),
                    request.resolution(),
                    request.shouldStore(),
                    status == OmniJobStatus.SUCCEEDED
                            ? "/api/omni/outputs/" + interactionId
                            : null,
                    durationSeconds,
                    errorCode,
                    providerStatus,
                    error,
                    retryable,
                    createdAt,
                    updatedAt,
                    "/api/omni/jobs/" + id
            );
        }
    }
}
