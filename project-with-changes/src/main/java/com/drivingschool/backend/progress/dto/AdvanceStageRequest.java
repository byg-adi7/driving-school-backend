package com.drivingschool.backend.progress.dto;

import com.drivingschool.backend.progress.enums.LicenseStage;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class AdvanceStageRequest {

    @NotNull(message = "Target stage is required")
    private final LicenseStage targetStage;

    private final String notes;
}
