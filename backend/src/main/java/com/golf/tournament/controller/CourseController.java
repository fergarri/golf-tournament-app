package com.golf.tournament.controller;

import com.golf.tournament.dto.course.CourseDTO;
import com.golf.tournament.dto.course.CourseTeeDTO;
import com.golf.tournament.dto.course.CreateCourseRequest;
import com.golf.tournament.dto.course.HoleDTO;
import com.golf.tournament.dto.course.ImportHandicapConversionResponse;
import com.golf.tournament.dto.course.PreviewHandicapImportResponse;
import com.golf.tournament.dto.course.TeeHandicapTableDTO;
import com.golf.tournament.service.CourseService;
import com.golf.tournament.service.HandicapConversionService;
import com.golf.tournament.service.HoleDistanceImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final HandicapConversionService handicapConversionService;
    private final HoleDistanceImportService holeDistanceImportService;

    @GetMapping
    public ResponseEntity<List<CourseDTO>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDTO> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CourseDTO>> searchCourses(@RequestParam String query) {
        return ResponseEntity.ok(courseService.searchCourses(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TOTAL')")
    public ResponseEntity<CourseDTO> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        CourseDTO course = courseService.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(course);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<CourseDTO> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TOTAL')")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courseId}/tees")
    public ResponseEntity<List<CourseTeeDTO>> getCourseTees(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getCourseTees(courseId));
    }

    @PostMapping("/{courseId}/tees")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<CourseTeeDTO> addTee(
            @PathVariable Long courseId,
            @RequestBody CourseTeeDTO teeDTO) {
        CourseTeeDTO tee = courseService.addTee(courseId, teeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(tee);
    }

    @PutMapping("/tees/{teeId}")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<CourseTeeDTO> updateTee(
            @PathVariable Long teeId,
            @RequestBody CourseTeeDTO teeDTO) {
        return ResponseEntity.ok(courseService.updateTee(teeId, teeDTO));
    }

    @DeleteMapping("/tees/{teeId}")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<Void> deleteTee(@PathVariable Long teeId) {
        courseService.deleteTee(teeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courseId}/holes")
    public ResponseEntity<List<HoleDTO>> getCourseHoles(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getCourseHoles(courseId));
    }

    @GetMapping("/{courseId}/handicap-conversions")
    public ResponseEntity<List<TeeHandicapTableDTO>> getHandicapConversions(@PathVariable Long courseId) {
        return ResponseEntity.ok(handicapConversionService.getTablesForCourse(courseId));
    }

    @PostMapping("/{courseId}/handicap-conversions/preview")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<PreviewHandicapImportResponse> previewHandicapConversions(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(handicapConversionService.previewImport(courseId, file));
    }

    @PostMapping("/{courseId}/handicap-conversions/import")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<ImportHandicapConversionResponse> importHandicapConversions(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "teeIds", required = false) List<Long> teeIds,
            @RequestParam(value = "createMissing", defaultValue = "false") boolean createMissing) {
        return ResponseEntity.ok(handicapConversionService.importTables(
                courseId, teeIds != null ? teeIds : List.of(), createMissing, file));
    }

    @PostMapping("/{courseId}/hole-distances/preview")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<PreviewHandicapImportResponse> previewHoleDistances(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(holeDistanceImportService.previewImport(courseId, file));
    }

    @PostMapping("/{courseId}/hole-distances/import")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<ImportHandicapConversionResponse> importHoleDistances(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "teeIds", required = false) List<Long> teeIds,
            @RequestParam(value = "createMissing", defaultValue = "false") boolean createMissing) {
        return ResponseEntity.ok(holeDistanceImportService.importDistances(
                courseId, teeIds != null ? teeIds : List.of(), createMissing, file));
    }

    @PostMapping("/{courseId}/holes")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<HoleDTO> addOrUpdateHole(
            @PathVariable Long courseId,
            @RequestBody HoleDTO holeDTO) {
        HoleDTO hole = courseService.addOrUpdateHole(courseId, holeDTO);
        return ResponseEntity.ok(hole);
    }
}
