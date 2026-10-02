package com.drivingschool.backend.attendance.service;

import com.drivingschool.backend.attendance.dto.DailyAttendanceResponse;
import com.drivingschool.backend.attendance.entity.DailyAttendance;
import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Printable Excel attendance sheets: a register (people x days, with totals) for a date
 * range, and a detailed list for one day. Laid out for paper - landscape, fitted to the
 * page width, header row repeated on every page, page numbers in the footer.
 */
@Service
public class AttendanceExportService {

    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEE\nd", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    private final Clock clock;

    public AttendanceExportService(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------------ register (grid)

    public byte[] register(AttendanceService.Register register) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet("Register");
            List<LocalDate> days = register.from().datesUntil(register.to().plusDays(1)).toList();
            int firstDayCol = 2;
            int totalsCol = firstDayCol + days.size();
            int lastCol = totalsCol + 3;

            title(sheet, styles, register.school().getName(), lastCol);
            line(sheet, styles.subtitle, 1, "Attendance register - " + who(register.role()) + " - "
                    + SHORT_DATE.format(register.from()) + " to " + SHORT_DATE.format(register.to()), lastCol);
            line(sheet, styles.note, 2, "P = present   L = late   A = absent   ? = awaiting confirmation   "
                    + "blank = not yet. Dates are " + register.school().getTimeZone() + " days. Printed "
                    + generatedAt(register.school()) + ".", lastCol);

            int headerRow = 4;
            Row header = sheet.createRow(headerRow);
            header.setHeightInPoints(30);
            cell(header, 0, "#", styles.header);
            cell(header, 1, "Name", styles.header);
            for (int i = 0; i < days.size(); i++) {
                cell(header, firstDayCol + i, DAY_HEADER.format(days.get(i)), styles.header);
            }
            String[] totals = {"Present", "Late", "Absent", "Pending"};
            for (int i = 0; i < totals.length; i++) {
                cell(header, totalsCol + i, totals[i], styles.header);
            }

            int rowIndex = headerRow + 1;
            int number = 1;
            for (AttendanceService.Person person : register.people()) {
                Row row = sheet.createRow(rowIndex++);
                cell(row, 0, String.valueOf(number++), styles.center);
                cell(row, 1, person.name(), styles.text);
                Map<LocalDate, DailyAttendance> mine = register.records().getOrDefault(person.userId(), Map.of());
                int present = 0, late = 0, absent = 0, pending = 0;
                for (int i = 0; i < days.size(); i++) {
                    LocalDate day = days.get(i);
                    DailyAttendance record = mine.get(day);
                    DailyAttendanceStatus status = record != null ? record.getStatus()
                            : day.isBefore(register.today()) ? DailyAttendanceStatus.ABSENT : null;
                    String mark = "";
                    CellStyle style = styles.center;
                    if (status == DailyAttendanceStatus.PRESENT) {
                        mark = "P"; present++; style = styles.present;
                    } else if (status == DailyAttendanceStatus.LATE) {
                        mark = "L"; late++; style = styles.late;
                    } else if (status == DailyAttendanceStatus.ABSENT) {
                        mark = "A"; absent++; style = styles.absent;
                    } else if (status == DailyAttendanceStatus.PENDING_CONFIRMATION) {
                        mark = "?"; pending++; style = styles.pending;
                    }
                    cell(row, firstDayCol + i, mark, style);
                }
                number(row, totalsCol, present, styles.total);
                number(row, totalsCol + 1, late, styles.total);
                number(row, totalsCol + 2, absent, styles.total);
                number(row, totalsCol + 3, pending, styles.total);
            }
            if (register.people().isEmpty()) {
                line(sheet, styles.note, rowIndex, "No " + who(register.role()).toLowerCase(Locale.ENGLISH) + " at this school.", lastCol);
            }

            sheet.setColumnWidth(0, 5 * 256);
            sheet.setColumnWidth(1, 28 * 256);
            for (int i = 0; i < days.size(); i++) {
                sheet.setColumnWidth(firstDayCol + i, 5 * 256);
            }
            for (int i = 0; i < 4; i++) {
                sheet.setColumnWidth(totalsCol + i, 9 * 256);
            }
            sheet.createFreezePane(firstDayCol, headerRow + 1);
            printLayout(sheet, headerRow);
            return write(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ one day (detailed)

    public byte[] day(School school, LocalDate date, RoleName role, List<DailyAttendanceResponse> rows) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(SHORT_DATE.format(date));
            String[] columns = {"#", "Name", "Status", "Check-in time", "Distance (m)", "Accuracy (m)",
                    "Confirmed by", "Recorded by", "Reason"};
            int lastCol = columns.length - 1;

            title(sheet, styles, school.getName(), lastCol);
            line(sheet, styles.subtitle, 1, "Attendance - " + who(role) + " - " + LONG_DATE.format(date), lastCol);
            line(sheet, styles.note, 2, "Times are " + school.getTimeZone() + ". Printed " + generatedAt(school) + ".", lastCol);

            int headerRow = 4;
            Row header = sheet.createRow(headerRow);
            for (int i = 0; i < columns.length; i++) {
                cell(header, i, columns[i], styles.header);
            }

            Map<DailyAttendanceStatus, Integer> counts = new EnumMap<>(DailyAttendanceStatus.class);
            int rowIndex = headerRow + 1;
            int number = 1;
            for (DailyAttendanceResponse entry : rows) {
                counts.merge(entry.getStatus(), 1, Integer::sum);
                Row row = sheet.createRow(rowIndex++);
                cell(row, 0, String.valueOf(number++), styles.center);
                cell(row, 1, entry.getName(), styles.text);
                cell(row, 2, label(entry.getStatus()), statusStyle(styles, entry.getStatus()));
                cell(row, 3, entry.getCheckedInAt() != null ? TIME.format(inSchoolTime(entry.getCheckedInAt(), school)) : "", styles.center);
                if (entry.getDistanceMeters() != null) {
                    number(row, 4, Math.round(entry.getDistanceMeters()), styles.center);
                } else {
                    cell(row, 4, "", styles.center);
                }
                if (entry.getAccuracyMeters() != null) {
                    number(row, 5, Math.round(entry.getAccuracyMeters()), styles.center);
                } else {
                    cell(row, 5, "", styles.center);
                }
                cell(row, 6, nullToEmpty(entry.getConfirmedByName()), styles.text);
                cell(row, 7, nullToEmpty(entry.getRecordedByName()), styles.text);
                cell(row, 8, nullToEmpty(entry.getReason()), styles.text);
            }

            rowIndex++;
            for (DailyAttendanceStatus status : DailyAttendanceStatus.values()) {
                Integer count = counts.get(status);
                if (count != null) {
                    Row row = sheet.createRow(rowIndex++);
                    cell(row, 1, label(status), styles.bold);
                    number(row, 2, count, styles.total);
                }
            }

            int[] widths = {5, 28, 22, 13, 13, 13, 22, 22, 36};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, headerRow + 1);
            printLayout(sheet, headerRow);
            return write(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void printLayout(Sheet sheet, int headerRow) {
        PrintSetup print = sheet.getPrintSetup();
        print.setLandscape(true);
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setHorizontallyCenter(true);
        sheet.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, -1, -1));
        sheet.getFooter().setCenter("Page &P of &N");
    }

    private void title(Sheet sheet, Styles styles, String text, int lastCol) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(22);
        cell(row, 0, text, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(lastCol, 1)));
    }

    private void line(Sheet sheet, CellStyle style, int rowIndex, String text, int lastCol) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, text, style);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, Math.max(lastCol, 1)));
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String generatedAt(School school) {
        return STAMP.format(LocalDateTime.now(clock.withZone(ZoneId.of(school.getTimeZone()))));
    }

    private static LocalDateTime inSchoolTime(LocalDateTime serverTime, School school) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of(school.getTimeZone())).toLocalDateTime();
    }

    private static String who(RoleName role) {
        return role == RoleName.INSTRUCTOR ? "Instructors" : "Students";
    }

    private static String label(DailyAttendanceStatus status) {
        return switch (status) {
            case PRESENT -> "Present";
            case LATE -> "Late";
            case ABSENT -> "Absent";
            case PENDING_CONFIRMATION -> "Awaiting confirmation";
            case NOT_CHECKED_IN -> "Not checked in";
        };
    }

    private static CellStyle statusStyle(Styles styles, DailyAttendanceStatus status) {
        return switch (status) {
            case PRESENT -> styles.present;
            case LATE -> styles.late;
            case ABSENT -> styles.absent;
            case PENDING_CONFIRMATION -> styles.pending;
            case NOT_CHECKED_IN -> styles.center;
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static byte[] write(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    /** Light fills only, so the sheet still reads well printed in black and white. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle note;
        final CellStyle header;
        final CellStyle text;
        final CellStyle bold;
        final CellStyle center;
        final CellStyle total;
        final CellStyle present;
        final CellStyle late;
        final CellStyle absent;
        final CellStyle pending;

        Styles(Workbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            Font noteFont = workbook.createFont();
            noteFont.setItalic(true);
            noteFont.setFontHeightInPoints((short) 9);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            subtitle = workbook.createCellStyle();
            subtitle.setFont(boldFont);
            note = workbook.createCellStyle();
            note.setFont(noteFont);

            header = bordered(workbook);
            header.setFont(boldFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            fill(header, IndexedColors.GREY_25_PERCENT);

            text = bordered(workbook);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);
            center = bordered(workbook);
            center.setAlignment(HorizontalAlignment.CENTER);
            total = bordered(workbook);
            total.setAlignment(HorizontalAlignment.CENTER);
            total.setFont(boldFont);

            present = centeredFill(workbook, IndexedColors.LIGHT_GREEN);
            late = centeredFill(workbook, IndexedColors.LIGHT_ORANGE);
            absent = centeredFill(workbook, IndexedColors.ROSE);
            pending = centeredFill(workbook, IndexedColors.LIGHT_YELLOW);
        }

        private static CellStyle bordered(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle centeredFill(Workbook workbook, IndexedColors color) {
            CellStyle style = bordered(workbook);
            style.setAlignment(HorizontalAlignment.CENTER);
            fill(style, color);
            return style;
        }

        private static void fill(CellStyle style, IndexedColors color) {
            style.setFillForegroundColor(color.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
    }
}
