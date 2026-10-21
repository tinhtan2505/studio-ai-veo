package studio.ai.omni.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.ai.omni.api.OmniJobRequest;
import studio.ai.omni.api.OmniMode;
import studio.ai.omni.config.OmniProperties;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadsMp4AndSidecarAfterRestart() throws Exception {
        OmniProperties properties = new OmniProperties();
        properties.setOutputDir(tempDir);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        OutputStore first = new OutputStore(properties, mapper);
        OmniJobRequest request = new OmniJobRequest(
                "SHOT-9", OmniMode.GENERATE, "prompt", null, null,
                List.of(), "9:16", "1080p", false
        );
        first.saveInline("interaction-9", request, new byte[]{0, 1, 2, 3}, "video/mp4", 7.5);

        OutputStore restarted = new OutputStore(properties, mapper);
        OutputStore.StoredOutput loaded = restarted.require("interaction-9");

        assertThat(loaded.metadata().shotId()).isEqualTo("SHOT-9");
        assertThat(loaded.metadata().stored()).isFalse();
        assertThat(loaded.metadata().durationSeconds()).isEqualTo(7.5);
        assertThat(loaded.resource().contentLength()).isEqualTo(4);
    }
}
