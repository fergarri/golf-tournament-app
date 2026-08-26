package com.golf.tournament.config;

import com.golf.tournament.model.Permission;
import com.golf.tournament.model.User;
import com.golf.tournament.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByMatricula(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        for (Permission permission : user.getRole().getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission.name()));
        }

        return new CustomUserDetails(
                user.getEmail(),
                user.getPassword(),
                authorities,
                user.getId(),
                user.getRole(),
                user.getCourse() != null ? user.getCourse().getId() : null,
                user.getCourse() != null ? user.getCourse().getNombre() : null
        );
    }
}
