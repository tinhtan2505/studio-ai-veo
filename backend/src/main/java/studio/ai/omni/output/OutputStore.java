package studio.ai.omni.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import studio.ai.omni.api.OmniJobRequest;
import studio.ai.omni.config.OmniProperties;
import studio.ai.omni.error.OmniApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Component
public class OutputStore {
    private final Path root;
    private final ObjectMapper objectMapper;

    public OutputStore(OmniProperties properties, ObjectMapper objectMapper) {
        this.root = properties.getOutputDir().toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo thư mục output Omni: " + root, exception);
        }
    }

    public Path createTempFile(String interactionId) {
        try {
            return Files.createTempFile(root, safeId(interactionId) + "-", ".part");
        } catch (IOException exception) {
            throw storageFailure("Không thể tạo tệp video tạm.", exception);
        }
    }

    public StoredOutput saveInline(String interactionId, OmniJobRequest request, byte[] bytes,
                                   String mimeType, Double durationSeconds) {
        Path temp = createTempFile(interactionId);
        try {
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            return commit(interactionId, request, temp, mimeType, durationSeconds);
        } catch (IOException exception) {
            deleteQuietly(temp);
            throw storageFailure("Không thể lưu video inline.", exception);
        }
    }

    public StoredOutput commit(String interactionId, OmniJobRequest request, Path tempFile,
                               String mimeType, Double durationSeconds) {
        String safeId = safeId(interactionId);
        Path video = checkedPath(safeId + ".mp4");
        Path metadataPath = checkedPath(safeId + ".json");
        Path metadataTemp = checkedPath(safeId + ".json.part");
        try {
            Files.move(tempFile, video, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            OutputMetadata metadata = new OutputMetadata(
                    interactionId,
                    request.shotId(),
                    request.mode(),
                    request.previousInteractionId(),
                    request.task(),
                    request.aspectRatio(),
                    request.resolution(),
                    request.shouldStore(),
                    mimeType == null || mimeType.isBlank() ? "video/mp4" : mimeType,
                    Files.size(video),
                    durationSeconds,
                    Instant.now()
            );
            objectMapper.writeValue(metadataTemp.toFile(), metadata);
            Files.move(metadataTemp, metadataPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new StoredOutput(metadata, video);
        } catch (IOException exception) {
            deleteQuietly(tempFile);
            deleteQuietly(metadataTemp);
            throw storageFailure("Không thể hoàn tất lưu video Omni.", exception);
        }
    }

    public StoredOutput require(String interactionId) {
        String safeId = safeId(interactionId);
        Path video = checkedPath(safeId + ".mp4");
        Path metadataPath = checkedPath(safeId + ".json");
        if (!Files.isRegularFile(video) || !Files.isRegularFile(metadataPath)) {
            throw new OmniApiException(
                    HttpStatus.NOT_FOUND, "OUTPUT_NOT_FOUND", "Không tìm thấy output video: " + interactionId + "."
            );
        }
        try {
            OutputMetadata metadata = objectMapper.readValue(metadataPath.toFile(), OutputMetadata.class);
            return new StoredOutput(metadata, video);
        } catch (IOException exception) {
            throw storageFailure("Metadata output bị hỏng hoặc không đọc được.", exception);
        }
    }

    public boolean exists(String interactionId) {
        try {
            String safeId = safeId(interactionId);
            return Files.isRegularFile(checkedPath(safeId + ".mp4"))
                    && Files.isRegularFile(checkedPath(safeId + ".json"));
        } catch (OmniApiException exception) {
            return false;
        }
    }

    public void delete(String interactionId) {
        String safeId = safeId(interactionId);
        deleteQuietly(checkedPath(safeId + ".mp4"));
        deleteQuietly(checkedPath(safeId + ".json"));
    }

    public Path root() {
        return root;
    }

    private String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,200}")) {
            throw new OmniApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_INTERACTION_ID", "Interaction ID không hợp lệ."
            );
        }
        return value;
    }

    private Path checkedPath(String fileName) {
        Path path = root.resolve(fileName).normalize();
        if (!path.startsWith(root)) {
            throw new OmniApiException(HttpStatus.BAD_REQUEST, "INVALID_OUTPUT_PATH", "Đường dẫn output không hợp lệ.");
        }
        return path;
    }

    private OmniApiException storageFailure(String message, IOException exception) {
        return new OmniApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "OUTPUT_STORAGE_FAILED",
                message,
                null,
                true
        );
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public record StoredOutput(OutputMetadata metadata, Path path) {
        public Resource resource() { return new FileSystemResource(path); }
    }
}
