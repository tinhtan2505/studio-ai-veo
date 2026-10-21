package studio.ai.omni.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import studio.ai.omni.api.MediaRole;
import studio.ai.omni.api.OmniJobRequest;
import studio.ai.omni.api.OmniMediaInput;
import studio.ai.omni.api.OmniMode;
import studio.ai.omni.api.OmniTask;
import studio.ai.omni.client.GeminiOmniClient;
import studio.ai.omni.client.GeminiOmniClient.FileInfo;
import studio.ai.omni.config.OmniProperties;
import studio.ai.omni.error.OmniApiException;
import studio.ai.omni.output.OutputStore;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class OmniServiceValidationTest {
    private GeminiOmniClient client;
    private OutputStore outputStore;
    private ExecutorService executor;
    private OmniService service;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(GeminiOmniClient.class);
        outputStore = Mockito.mock(OutputStore.class);
        executor = Executors.newSingleThreadExecutor();
        service = new OmniService(client, new OmniProperties(), outputStore, executor);
        when(client.getFile(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            boolean image = id.startsWith("image");
            Double duration = null;
            if (id.contains("long-source")) duration = 10.1;
            else if (id.contains("long-ref")) duration = 3.1;
            else if (!image) duration = 3.0;
            return file(id, image ? "image/png" : "video/mp4", duration);
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    static Stream<OmniJobRequest> invalidRequests() {
        return Stream.of(
                request(OmniMode.GENERATE, null, List.of(
                        media("image-last", MediaRole.LAST_FRAME, 0)
                ), null),
                request(OmniMode.GENERATE, null, List.of(
                        media("image-a", MediaRole.FIRST_FRAME, 0),
                        media("image-b", MediaRole.FIRST_FRAME, 1)
                ), null),
                request(OmniMode.GENERATE, null, List.of(
                        media("video-1", MediaRole.VIDEO_REFERENCE, 0),
                        media("video-2", MediaRole.VIDEO_REFERENCE, 1),
                        media("video-3", MediaRole.VIDEO_REFERENCE, 2),
                        media("video-4", MediaRole.VIDEO_REFERENCE, 3)
                ), null),
                request(OmniMode.EDIT, null, List.of(), OmniTask.EDIT),
                request(OmniMode.EDIT, "turn-1", List.of(
                        media("video-source", MediaRole.SOURCE_VIDEO, 0)
                ), OmniTask.EDIT),
                request(OmniMode.EDIT, null, List.of(
                        media("video-source", MediaRole.SOURCE_VIDEO, 0),
                        media("video-source-2", MediaRole.SOURCE_VIDEO, 1)
                ), OmniTask.EDIT),
                request(OmniMode.GENERATE, null, List.of(
                        media("image-a", MediaRole.IMAGE_REFERENCE, 0),
                        media("image-a", MediaRole.IMAGE_REFERENCE, 1)
                ), null),
                request(OmniMode.GENERATE, null, List.of(
                        media("image-a", MediaRole.IMAGE_REFERENCE, 0),
                        media("image-b", MediaRole.IMAGE_REFERENCE, 0)
                ), null),
                request(OmniMode.EXTEND, "turn-1", List.of(), OmniTask.EDIT),
                request(OmniMode.GENERATE, null, List.of(), OmniTask.EXTEND)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRoleSourceDurationOrderAndTaskCombinations(OmniJobRequest request) {
        assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(OmniApiException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_OMNI_REQUEST");
    }

    @Test
    void rejectsMediaThatIsStillProcessing() {
        when(client.getFile("image-processing")).thenReturn(new FileInfo(
                "image-processing", "files/image-processing", "image.png", "uri", "",
                "image/png", 12, "PROCESSING", null, Instant.now().plusSeconds(3600), ""
        ));
        OmniJobRequest request = request(OmniMode.GENERATE, null, List.of(
                media("image-processing", MediaRole.FIRST_FRAME, 0)
        ), OmniTask.IMAGE_TO_VIDEO);

        var accepted = service.start(request);

        assertThat(waitUntilDone(accepted.id()).errorCode()).isEqualTo("MEDIA_NOT_READY");
    }

    static Stream<OmniJobRequest> invalidProviderMedia() {
        return Stream.of(
                request(OmniMode.EDIT, null, List.of(
                        media("video-long-source", MediaRole.SOURCE_VIDEO, 0)
                ), OmniTask.EDIT),
                request(OmniMode.GENERATE, null, List.of(
                        media("video-long-ref", MediaRole.VIDEO_REFERENCE, 0)
                ), null),
                request(OmniMode.GENERATE, null, List.of(
                        media("video-source", MediaRole.IMAGE_REFERENCE, 0)
                ), null)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidProviderMedia")
    void failsJobForInvalidProviderMimeOrDuration(OmniJobRequest request) {
        var accepted = service.start(request);

        assertThat(waitUntilDone(accepted.id()).errorCode()).isEqualTo("INVALID_OMNI_REQUEST");
    }

    @Test
    void returnsQueuedBeforeProviderLookupCompletes() throws Exception {
        CountDownLatch lookupStarted = new CountDownLatch(1);
        CountDownLatch releaseLookup = new CountDownLatch(1);
        when(client.getFile("image-slow")).thenAnswer(invocation -> {
            lookupStarted.countDown();
            releaseLookup.await(2, TimeUnit.SECONDS);
            return file("image-slow", "image/png", null);
        });
        OmniJobRequest request = request(OmniMode.GENERATE, null, List.of(
                media("image-slow", MediaRole.FIRST_FRAME, 0)
        ), OmniTask.IMAGE_TO_VIDEO);

        var accepted = service.start(request);
        try {
            assertThat(accepted.status()).isEqualTo(studio.ai.omni.api.OmniJobStatus.QUEUED);
            assertThat(lookupStarted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseLookup.countDown();
        }
    }

    private studio.ai.omni.api.OmniJobResponse waitUntilDone(java.util.UUID id) {
        for (int attempt = 0; attempt < 100; attempt++) {
            var response = service.job(id);
            if (response.done()) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("Omni job did not finish in time");
    }

    private static OmniJobRequest request(OmniMode mode, String previous,
                                          List<OmniMediaInput> media, OmniTask task) {
        return new OmniJobRequest("SHOT-1", mode, "prompt", task, previous, media, "16:9", "720p", true);
    }

    private static OmniMediaInput media(String id, MediaRole role, int order) {
        return new OmniMediaInput(id, role, order);
    }

    private static FileInfo file(String id, String mimeType, Double duration) {
        return new FileInfo(
                id, "files/" + id, id, "https://files/" + id, "https://download/" + id,
                mimeType, 100, "ACTIVE", duration, Instant.now().plusSeconds(3600), ""
        );
    }
}
