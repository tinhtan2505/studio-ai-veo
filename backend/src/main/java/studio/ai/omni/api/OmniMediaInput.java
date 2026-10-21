package studio.ai.omni.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record OmniMediaInput(
        @NotBlank String fileId,
        @NotNull MediaRole role,
        @PositiveOrZero Integer order
) {
}
