package com.drivingschool.backend.lesson.route.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteCoordinateDTO {

    private Double latitude;
    private Double longitude;
}