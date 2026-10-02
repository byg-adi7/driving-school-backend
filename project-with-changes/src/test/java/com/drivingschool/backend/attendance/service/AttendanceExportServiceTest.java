package com.drivingschool.backend.attendance.service;

import com.drivingschool.backend.attendance.dto.DailyAttendanceResponse;
import com.drivingschool.backend.attendance.entity.DailyAttendance;
import com.drivingschool.backend.attendance.enums.AttendanceSource;
import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceExportServiceTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 28);
    private final AttendanceExportService export =
            new AttendanceExportService(Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
    private final DataFormatter text = new DataFormatter();

    private School school() {
        return School.builder().name("Aidly Driving").address("Accra").active(true).build();
    }

    private DailyAttendance record(LocalDate day, DailyAttendanceStatus status) {
        return DailyAttendance.builder().attendanceDate(day).status(status).role(RoleName.STUDENT)
                .source(AttendanceSource.CHECK_IN).build();
    }

    private String at(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        return r == null || r.getCell(col) == null ? "" : text.formatCellValue(r.getCell(col));
    }

    @Test
    void theRegister_isAGridOfPeopleByDays_withMarksTotalsAndAPrintLayout() throws IOException {
        // Mon 28 Sep - Thu 1 Oct; "today" is Wed 30 Sep, so Thu is still blank.
        var people = List.of(new AttendanceService.Person(2L, "Ama Mensah", RoleName.STUDENT),
                new AttendanceService.Person(4L, "Kofi Boateng", RoleName.STUDENT));
        Map<Long, Map<LocalDate, DailyAttendance>> records = Map.of(2L, Map.of(
                MON, record(MON, DailyAttendanceStatus.PRESENT),
                MON.plusDays(1), record(MON.plusDays(1), DailyAttendanceStatus.LATE),
                MON.plusDays(2), record(MON.plusDays(2), DailyAttendanceStatus.PENDING_CONFIRMATION)));
        var register = new AttendanceService.Register(school(), RoleName.STUDENT, MON, MON.plusDays(3),
                MON.plusDays(2), people, records);

        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(export.register(register)))) {
            Sheet sheet = book.getSheet("Register");
            assertThat(at(sheet, 0, 0)).isEqualTo("Aidly Driving");
            assertThat(at(sheet, 1, 0)).contains("Students").contains("28 Sep 2026 to 1 Oct 2026");
            assertThat(at(sheet, 4, 1)).isEqualTo("Name");
            assertThat(at(sheet, 4, 2)).isEqualTo("Mon\n28");
            assertThat(at(sheet, 4, 6)).isEqualTo("Present");

            // Ama: P, L, ? and Thursday blank; totals present 1, late 1, absent 0, pending 1.
            assertThat(List.of(at(sheet, 5, 1), at(sheet, 5, 2), at(sheet, 5, 3), at(sheet, 5, 4), at(sheet, 5, 5)))
                    .containsExactly("Ama Mensah", "P", "L", "?", "");
            assertThat(List.of(at(sheet, 5, 6), at(sheet, 5, 7), at(sheet, 5, 8), at(sheet, 5, 9)))
                    .containsExactly("1", "1", "0", "1");
            // Kofi never checked in: absent on the past days, blank from today on.
            assertThat(List.of(at(sheet, 6, 2), at(sheet, 6, 3), at(sheet, 6, 4), at(sheet, 6, 8)))
                    .containsExactly("A", "A", "", "2");

            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
            assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
            assertThat(sheet.getRepeatingRows().formatAsString()).isEqualTo("5:5");
        }
    }

    @Test
    void theDayList_showsStatusTimeAndDistance_andASummary() throws IOException {
        LocalDate day = LocalDate.of(2026, 10, 1);
        List<DailyAttendanceResponse> rows = List.of(
                DailyAttendanceResponse.builder().name("Ama Mensah").status(DailyAttendanceStatus.PRESENT)
                        .checkedInAt(LocalDateTime.of(2026, 10, 1, 8, 5)).distanceMeters(42.4).accuracyMeters(9.0)
                        .lessonType(com.drivingschool.backend.attendance.enums.LessonType.THEORY).topic("Road signs")
                        .confirmedByName("Ina Instructor").build(),
                DailyAttendanceResponse.builder().name("Esi Owusu").status(DailyAttendanceStatus.PENDING_CONFIRMATION)
                        .confirmationReason(com.drivingschool.backend.attendance.enums.ConfirmationReason.OUTSIDE_SCHOOL_AREA)
                        .lessonType(com.drivingschool.backend.attendance.enums.LessonType.PRACTICAL).build(),
                DailyAttendanceResponse.builder().name("Kofi Boateng").status(DailyAttendanceStatus.NOT_CHECKED_IN).build());

        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(export.day(school(), day, RoleName.STUDENT, rows)))) {
            Sheet sheet = book.getSheetAt(0);
            assertThat(at(sheet, 1, 0)).contains("Thursday 1 October 2026");
            assertThat(List.of(at(sheet, 5, 1), at(sheet, 5, 2), at(sheet, 5, 3), at(sheet, 5, 4), at(sheet, 5, 6), at(sheet, 5, 8)))
                    .containsExactly("Ama Mensah", "Present", "Theory", "Road signs", "42", "Ina Instructor");
            assertThat(List.of(at(sheet, 6, 2), at(sheet, 6, 3)))
                    .containsExactly("Awaiting confirmation (outside school area)", "Practical");
            assertThat(at(sheet, 7, 2)).isEqualTo("Not checked in");
            assertThat(List.of(at(sheet, 9, 1), at(sheet, 9, 2))).containsExactly("Present", "1");
        }
    }
}
