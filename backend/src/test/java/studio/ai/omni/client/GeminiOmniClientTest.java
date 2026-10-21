package studio.ai.omni.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import studio.ai.omni.api.OmniJobRequest;
import studio.ai.omni.api.OmniMode;
import studio.ai.omni.client.GeminiOmniClient.FileInfo;
import studio.ai.omni.client.GeminiOmniClient.InteractionResult;
import studio.ai.omni.config.OmniProperties;
import studio.ai.omni.error.OmniApiException;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiOmniClientTest {
    @TempDir
    Path tempDir;
    private MockWebServer server;
    private GeminiOmniClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OmniProperties properties = new OmniProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl(server.url("/").toString());
        client = new GeminiOmniClient(
                new ObjectMapper().findAndRegisterModules(), HttpClient.newHttpClient(), properties
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void createsStoredSynchronousInteractionAndExtractsUriVideo() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                {"id":"interaction-1","status":"completed","steps":[
                  {"type":"model_output","content":[
                    {"type":"video","uri":"https://example.test/v1beta/files/generated-1","mime_type":"video/mp4"}
                  ]}
                ]}
                """));
        OmniJobRequest request = new OmniJobRequest(
                "SHOT-1", OmniMode.GENERATE, "a crane shot", null, null,
                List.of(), "16:9", "4k", true
        );

        InteractionResult result = client.createInteraction(request, List.of());
        RecordedRequest upstream = server.takeRequest();

        assertThat(upstream.getPath()).isEqualTo("/v1beta/interactions");
        assertThat(upstream.getHeader("x-goog-api-key")).isEqualTo("test-key");
        assertThat(upstream.getBody().readUtf8())
                .contains("gemini-omni-1.1-flash", "\"background\":false", "\"stream\":false")
                .contains("\"delivery\":\"uri\"");
        assertThat(result.id()).isEqualTo("interaction-1");
        assertThat(result.video().uri()).contains("generated-1");
    }

    @Test
    void extractsFilesApiIdFromDownloadUri() {
        assertThat(GeminiOmniClient.fileIdFromUri(
                "https://generativelanguage.googleapis.com/v1beta/files/abc-123:download?alt=media"
        )).isEqualTo("abc-123");
    }

    @Test
    void extractsInlineVideoAsFallback() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                {"id":"inline-1","steps":[{"type":"model_output","content":[
                  {"type":"video","data":"AAEC","mime_type":"video/mp4"}
                ]}]}
                """));
        OmniJobRequest request = new OmniJobRequest(
                "SHOT-1", OmniMode.GENERATE, "prompt", null, null,
                List.of(), "16:9", "720p", true
        );

        InteractionResult result = client.createInteraction(request, List.of());

        assertThat(client.decodeInlineVideo(result.video())).containsExactly(0, 1, 2);
    }

    @Test
    void preservesSafetyBlockFromCompletedHttpResponse() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                {"id":"blocked-1","status":"failed","error":{"status":"SAFETY_BLOCKED"}}
                """));
        OmniJobRequest request = new OmniJobRequest(
                "SHOT-1", OmniMode.GENERATE, "prompt", null, null,
                List.of(), "16:9", "720p", true
        );

        assertThatThrownBy(() -> client.createInteraction(request, List.of()))
                .isInstanceOfSatisfying(OmniApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("SAFETY_BLOCKED");
                    assertThat(error.isRetryable()).isFalse();
                });
    }

    @Test
    void downloadsGeneratedMp4ToLocalStorage() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "video/mp4")
                .setBody("fake-mp4"));
        Path destination = tempDir.resolve("output.part");

        client.download(server.url("/generated/output.mp4").toString(), destination);

        assertThat(Files.readString(destination)).isEqualTo("fake-mp4");
        assertThat(server.takeRequest().getHeader("x-goog-api-key")).isEqualTo("test-key");
    }

    @Test
    void performsResumableUploadAndReadsVideoMetadata() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("x-goog-upload-url", server.url("/resumable/session-1")));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                {"file":{"name":"files/file-1","displayName":"source.mp4","uri":"uri-1",
                "mimeType":"video/mp4","sizeBytes":"4","state":"PROCESSING",
                "videoMetadata":{"videoDuration":"2.5s"}}}
                """));

        FileInfo result = client.upload(new MockMultipartFile(
                "file", "source.mp4", "video/mp4", new byte[]{0, 1, 2, 3}
        ));
        RecordedRequest start = server.takeRequest();
        RecordedRequest upload = server.takeRequest();

        assertThat(start.getPath()).isEqualTo("/upload/v1beta/files");
        assertThat(start.getHeader("X-Goog-Upload-Protocol")).isEqualTo("resumable");
        assertThat(upload.getHeader("X-Goog-Upload-Command")).isEqualTo("upload, finalize");
        assertThat(upload.getBodySize()).isEqualTo(4);
        assertThat(result.durationSeconds()).isEqualTo(2.5);
        assertThat(result.state()).isEqualTo("PROCESSING");
    }

    @Test
    void mapsRateLimitAndSafetyWithoutLeakingProviderBody() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("""
                {"error":{"status":"RESOURCE_EXHAUSTED","message":"quota for secret prompt"}}
                """));
        assertThatThrownBy(() -> client.getFile("file-1"))
                .isInstanceOfSatisfying(OmniApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(error.isRetryable()).isTrue();
                    assertThat(error.getMessage()).doesNotContain("secret prompt");
                });

        server.enqueue(new MockResponse().setResponseCode(400).setBody("""
                {"error":{"status":"INVALID_ARGUMENT","message":"SAFETY blocked secret prompt"}}
                """));
        assertThatThrownBy(() -> client.getFile("file-1"))
                .isInstanceOfSatisfying(OmniApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("SAFETY_BLOCKED");
                    assertThat(error.getMessage()).doesNotContain("secret prompt");
                });
    }

    @Test
    void reportsExpiredFilesAsReuploadableMedia() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));

        assertThatThrownBy(() -> client.getFile("expired-file"))
                .isInstanceOfSatisfying(OmniApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("MEDIA_EXPIRED");
                    assertThat(error.getHttpStatus().value()).isEqualTo(410);
                    assertThat(error.isRetryable()).isFalse();
                });
    }

    @Test
    void mapsBadRequestAndProviderFailureRetryability() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}"));
        assertThatThrownBy(() -> client.getFile("file-1"))
                .isInstanceOfSatisfying(OmniApiException.class, error -> {
                    assertThat(error.getHttpStatus().value()).isEqualTo(400);
                    assertThat(error.isRetryable()).isFalse();
                });

        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\":{\"status\":\"UNAVAILABLE\"}}"));
        assertThatThrownBy(() -> client.getFile("file-1"))
                .isInstanceOfSatisfying(OmniApiException.class, error -> {
                    assertThat(error.getHttpStatus().value()).isEqualTo(502);
                    assertThat(error.isRetryable()).isTrue();
                });
    }
}
