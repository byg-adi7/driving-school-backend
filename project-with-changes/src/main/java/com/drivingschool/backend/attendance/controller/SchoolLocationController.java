package com.drivingschool.backend.attendance.controller;

import com.drivingschool.backend.attendance.dto.UpdateSchoolLocationRequest;
import com.drivingschool.backend.attendance.service.AttendanceService;
import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.school.dto.SchoolResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where a school's attendance check-ins are measured from (and its time zone). */
@RestController
@RequestMapping("/api/v1/schools")
@Tag(name = "Attendance", description = "Daily location check-in and attendance registers")
@SecurityRequirement(name = "Bearer Authentication")
public class SchoolLocationController {

    private final AttendanceService attendanceService;

    public SchoolLocationController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PutMapping("/me/location")
    @Operation(summary = "Set my school's location, check-in radius and time zone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SchoolResponse>> setMine(@Valid @RequestBody UpdateSchoolLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("School location saved", attendanceService.updateSchoolLocation(null, request)));
    }

    @PutMapping("/{schoolId}/location")
    @Operation(summary = "Set a school's location, check-in radius and time zone (bootstrap admin: any school)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SchoolResponse>> set(@PathVariable Long schoolId,
                                                           @Valid @RequestBody UpdateSchoolLocationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("School location saved", attendanceService.updateSchoolLocation(schoolId, request)));
    }
}
