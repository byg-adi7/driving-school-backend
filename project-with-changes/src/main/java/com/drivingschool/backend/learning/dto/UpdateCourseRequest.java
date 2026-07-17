package com.drivingschool.backend.learning.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateCourseRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;
}
