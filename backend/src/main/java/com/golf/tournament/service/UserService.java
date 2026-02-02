package com.golf.tournament.service;

import com.golf.tournament.dto.user.ChangePasswordRequest;
import com.golf.tournament.dto.user.CreateUserRequest;
import com.golf.tournament.dto.user.UpdateUserRequest;
import com.golf.tournament.dto.user.UserDTO;
import com.golf.tournament.exception.DuplicateResourceException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.User;
import com.golf.tournament.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
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

        User user = User.builder()
                .email(request.getEmail())
                .matricula(request.getMatricula())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        user = userRepository.save(user);
        log.info("User created with id: {}", user.getId());
        return convertToDTO(user);
    }

    @Transactional
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (!user.getEmail().equals(request.getEmail()) &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        if (request.getMatricula() != null && !request.getMatricula().isEmpty() &&
                !request.getMatricula().equals(user.getMatricula()) &&
                userRepository.existsByMatricula(request.getMatricula())) {
            throw new DuplicateResourceException("User", "matricula", request.getMatricula());
        }

        user.setEmail(request.getEmail());
        user.setMatricula(request.getMatricula());
        user.setRole(request.getRole());

        user = userRepository.save(user);
        log.info("User updated with id: {}", user.getId());
        return convertToDTO(user);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user id: {}", id);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", "id", id);
        }
        userRepository.deleteById(id);
        log.info("User deleted with id: {}", id);
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .matricula(user.getMatricula())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
