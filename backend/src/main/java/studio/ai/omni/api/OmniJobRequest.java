package studio.ai.omni.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Comparator;

public record OmniJobRequest(
        @NotBlank @Size(max = 160) String shotId,
        @NotNull OmniMode mode,
        @NotBlank @Size(max = 100_000) String prompt,
        OmniTask task,
        @Size(max = 200) String previousInteractionId,
        List<@Valid OmniMediaInput> media,
        @NotBlank @Pattern(regexp = "16:9|9:16") String aspectRatio,
        @NotBlank @Pattern(regexp = "360p|720p|1080p|4k") String resolution,
        Boolean store
) {
    public List<OmniMediaInput> safeMedia() {
        if (media == null) return List.of();
        return media.stream()
                .sorted(Comparator.comparing(input -> input.order() == null ? Integer.MAX_VALUE : input.order()))
                .toList();
    }

    public boolean shouldStore() {
        return store == null || store;
    }
}
