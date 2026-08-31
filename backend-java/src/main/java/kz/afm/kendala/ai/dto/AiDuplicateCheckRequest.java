package kz.afm.kendala.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiDuplicateCheckRequest(
        @JsonProperty("new_application") AiDuplicateApplication newApplication,
        @JsonProperty("existing_applications") List<AiDuplicateApplication> existingApplications
) {
}
