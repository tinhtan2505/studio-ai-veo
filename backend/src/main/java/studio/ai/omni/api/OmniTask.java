package studio.ai.omni.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum OmniTask {
    TEXT_TO_VIDEO,
    IMAGE_TO_VIDEO,
    REFERENCE_TO_VIDEO,
    EDIT,
    EXTEND;

    @JsonCreator
    public static OmniTask fromJson(String value) {
        if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value)) return null;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
