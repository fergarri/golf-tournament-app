package com.golf.tournament.service;

import com.golf.tournament.dto.user.ChangePasswordRequest;
import com.golf.tournament.dto.user.CreateUserRequest;
import com.golf.tournament.dto.user.UpdateUserRequest;
import com.golf.tournament.dto.user.UserDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.DuplicateResourceException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Course;
import com.golf.tournament.model.Role;
import com.golf.tournament.model.User;
import com.golf.tournament.repository.CourseRepository;
import com.golf.tournament.repository.UserRepository;
import com.golf.tournament.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        List<User> users = currentUserProvider.isSuperAdmin()
                ? userRepository.findAll()
                : userRepository.findByCourseId(currentUserProvider.getCurrentCourseId());
        return users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        assertManageableByCurrentUser(user);
        return convertToDTO(user);
    }

    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        if (request.getMatricula() != null && !request.getMatricula().isEmpty() &&
                userRepository.existsByMatricula(request.getMatricula())) {
            throw new DuplicateResourceException("User", "matricula", request.getMatricula());
        }

        Role role = parseRole(request.getRole());
        assertRoleAssignable(role);
        Course course = resolveCourseForRole(role, request.getCourseId());

        User user = User.builder()
                .email(request.getEmail())
                .matricula(request.getMatricula())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .course(course)
                .build();

        user = userRepository.save(user);
        log.info("User created with id: {}", user.getId());
        return convertToDTO(user);
    }

    @Transactional
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        assertManageableByCurrentUser(user);

        if (!user.getEmail().equals(request.getEmail()) &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        if (request.getMatricula() != null && !request.getMatricula().isEmpty() &&
                !request.getMatricula().equals(user.getMatricula()) &&
                userRepository.existsByMatricula(request.getMatricula())) {
            throw new DuplicateResourceException("User", "matricula", request.getMatricula());
        }

        Role role = parseRole(request.getRole());
        assertRoleAssignable(role);
        Course course = resolveCourseForRole(role, request.getCourseId());

        user.setEmail(request.getEmail());
        user.setMatricula(request.getMatricula());
        user.setRole(role);
        user.setCourse(course);

        user = userRepository.save(user);
        log.info("User updated with id: {}", user.getId());
        return convertToDTO(user);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        assertManageableByCurrentUser(user);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user id: {}", id);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        assertManageableByCurrentUser(user);
        userRepository.deleteById(id);
        log.info("User deleted with id: {}", id);
    }

    private Role parseRole(String roleName) {
        try {
            return Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol no válido: " + roleName);
        }
    }

    /**
     * Un admin de club (ADMIN_CLUB) sólo puede crear/editar usuarios con rol USER o
     * ADMIN_CLUB, nunca ADMIN (superadmin). El superadmin puede asignar cualquier rol.
     */
    private void assertRoleAssignable(Role role) {
        if (!currentUserProvider.isSuperAdmin() && role == Role.ADMIN) {
            throw new AccessDeniedException("No tiene permisos para asignar el rol ADMIN");
        }
    }

    /**
     * Un admin de club (ADMIN_CLUB) sólo puede ver/editar/eliminar usuarios de su propio
     * club (nunca al superadmin ni usuarios de otros clubes). El superadmin puede
     * gestionar cualquier usuario.
     */
    private void assertManageableByCurrentUser(User user) {
        if (currentUserProvider.isSuperAdmin()) {
            return;
        }
        Long targetCourseId = user.getCourse() != null ? user.getCourse().getId() : null;
        currentUserProvider.assertClubAccess(targetCourseId);
    }

    /**
     * Los superadmins (rol ADMIN) no tienen club asignado. Los usuarios con rol USER o
     * ADMIN_CLUB ("admin de club") deben tener un club válido. Un admin de club sólo
     * puede asignar su propio club, sin importar lo que llegue en el request.
     */
    private Course resolveCourseForRole(Role role, Long courseId) {
        if (role == Role.ADMIN) {
            return null;
        }
        Long effectiveCourseId = currentUserProvider.isSuperAdmin() ? courseId : currentUserProvider.getCurrentCourseId();
        if (effectiveCourseId == null) {
            throw new BadRequestException("Debe seleccionar el club para un usuario con rol " + role.name());
        }
        return courseRepository.findById(effectiveCourseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", effectiveCourseId));
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .matricula(user.getMatricula())
                .role(user.getRole().name())
                .courseId(user.getCourse() != null ? user.getCourse().getId() : null)
                .courseName(user.getCourse() != null ? user.getCourse().getNombre() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
