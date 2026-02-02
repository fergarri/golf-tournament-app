package com.golf.tournament.service;

import com.golf.tournament.dto.course.CourseDTO;
import com.golf.tournament.dto.course.CourseTeeDTO;
import com.golf.tournament.dto.course.CreateCourseRequest;
import com.golf.tournament.dto.course.HoleDTO;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseTeeRepository courseTeeRepository;
    private final HoleRepository holeRepository;
    private final HoleDistanceRepository holeDistanceRepository;

    @Transactional(readOnly = true)
    public List<CourseDTO> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CourseDTO getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        return convertToDTO(course);
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> searchCourses(String search) {
        return courseRepository.searchCourses(search).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CourseDTO createCourse(CreateCourseRequest request) {
        Course course = Course.builder()
                .nombre(request.getNombre())
                .pais(request.getPais())
                .provincia(request.getProvincia())
                .ciudad(request.getCiudad())
                .cantidadHoyos(request.getCantidadHoyos())
                .courseRating(request.getCourseRating())
                .slopeRating(request.getSlopeRating())
                .build();

        course = courseRepository.save(course);
        log.info("Course created with id: {}", course.getId());
        return convertToDTO(course);
    }

    @Transactional
    public CourseDTO updateCourse(Long id, CreateCourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

        course.setNombre(request.getNombre());
        course.setPais(request.getPais());
        course.setProvincia(request.getProvincia());
        course.setCiudad(request.getCiudad());
        course.setCantidadHoyos(request.getCantidadHoyos());
        course.setCourseRating(request.getCourseRating());
        course.setSlopeRating(request.getSlopeRating());

        course = courseRepository.save(course);
        log.info("Course updated with id: {}", course.getId());
        return convertToDTO(course);
    }

    @Transactional
    public void deleteCourse(Long id) {
        if (!courseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course", "id", id);
        }
        courseRepository.deleteById(id);
        log.info("Course deleted with id: {}", id);
    }

    @Transactional
    public CourseTeeDTO addTee(Long courseId, CourseTeeDTO teeDTO) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        CourseTee tee = CourseTee.builder()
                .course(course)
                .nombre(teeDTO.getNombre())
                .grupo(teeDTO.getGrupo())
                .active(true)
                .build();

        tee = courseTeeRepository.save(tee);
        log.info("Tee added to course {}: {}", courseId, tee.getId());
        return convertTeeToDTO(tee);
    }

    @Transactional
    public CourseTeeDTO updateTee(Long teeId, CourseTeeDTO teeDTO) {
        CourseTee tee = courseTeeRepository.findById(teeId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeId));

        tee.setNombre(teeDTO.getNombre());
        tee.setGrupo(teeDTO.getGrupo());
        if (teeDTO.getActive() != null) {
            tee.setActive(teeDTO.getActive());
        }

        tee = courseTeeRepository.save(tee);
        log.info("Tee updated: {}", teeId);
        return convertTeeToDTO(tee);
    }

    @Transactional
    public void deactivateTee(Long teeId) {
        CourseTee tee = courseTeeRepository.findById(teeId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeId));
        tee.setActive(false);
        courseTeeRepository.save(tee);
        log.info("Tee deactivated: {}", teeId);
    }

    @Transactional(readOnly = true)
    public List<CourseTeeDTO> getCourseTees(Long courseId) {
        return courseTeeRepository.findByCourseId(courseId).stream()
                .map(this::convertTeeToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public HoleDTO addOrUpdateHole(Long courseId, HoleDTO holeDTO) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        Hole hole = holeRepository.findByCourseIdAndNumeroHoyo(courseId, holeDTO.getNumeroHoyo())
                .orElse(Hole.builder()
                        .course(course)
                        .numeroHoyo(holeDTO.getNumeroHoyo())
                        .build());

        hole.setPar(holeDTO.getPar());
        hole.setHandicap(holeDTO.getHandicap());

        hole = holeRepository.save(hole);

        if (holeDTO.getDistancesByTee() != null) {
            for (Map.Entry<Long, Integer> entry : holeDTO.getDistancesByTee().entrySet()) {
                Long teeId = entry.getKey();
                Integer distance = entry.getValue();

                CourseTee tee = courseTeeRepository.findById(teeId)
                        .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeId));

                HoleDistance holeDistance = holeDistanceRepository
                        .findByHoleIdAndCourseTeeId(hole.getId(), teeId)
                        .orElse(HoleDistance.builder()
                                .hole(hole)
                                .courseTee(tee)
                                .build());

                holeDistance.setDistanciaYardas(distance);
                holeDistanceRepository.save(holeDistance);
            }
        }

        log.info("Hole added/updated for course {}: hole {}", courseId, holeDTO.getNumeroHoyo());
        return convertHoleToDTO(hole);
    }

    @Transactional(readOnly = true)
    public List<HoleDTO> getCourseHoles(Long courseId) {
        return holeRepository.findByCourseIdOrderByNumeroHoyoAsc(courseId).stream()
                .map(this::convertHoleToDTO)
                .collect(Collectors.toList());
    }

    private CourseDTO convertToDTO(Course course) {
        List<CourseTeeDTO> tees = course.getTees() != null ?
                course.getTees().stream().map(this::convertTeeToDTO).collect(Collectors.toList()) :
                new ArrayList<>();

        List<HoleDTO> holes = course.getHoles() != null ?
                course.getHoles().stream().map(this::convertHoleToDTO).collect(Collectors.toList()) :
                new ArrayList<>();

        return CourseDTO.builder()
                .id(course.getId())
                .nombre(course.getNombre())
                .pais(course.getPais())
                .provincia(course.getProvincia())
                .ciudad(course.getCiudad())
                .cantidadHoyos(course.getCantidadHoyos())
                .courseRating(course.getCourseRating())
                .slopeRating(course.getSlopeRating())
                .tees(tees)
                .holes(holes)
                .build();
    }

    private CourseTeeDTO convertTeeToDTO(CourseTee tee) {
        return CourseTeeDTO.builder()
                .id(tee.getId())
                .courseId(tee.getCourse().getId())
                .nombre(tee.getNombre())
                .grupo(tee.getGrupo())
                .active(tee.getActive())
                .build();
    }

    private HoleDTO convertHoleToDTO(Hole hole) {
        Map<Long, Integer> distancesByTee = new HashMap<>();
        if (hole.getDistances() != null) {
            for (HoleDistance distance : hole.getDistances()) {
                distancesByTee.put(distance.getCourseTee().getId(), distance.getDistanciaYardas());
            }
        }

        return HoleDTO.builder()
                .id(hole.getId())
                .numeroHoyo(hole.getNumeroHoyo())
                .par(hole.getPar())
                .handicap(hole.getHandicap())
                .distancesByTee(distancesByTee)
                .build();
    }
}
