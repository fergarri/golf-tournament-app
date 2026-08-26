package com.golf.tournament.config;

import com.golf.tournament.model.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * UserDetails extendido que además expone el rol y el club (courseId) del usuario
 * autenticado, necesarios para acotar el acceso a los datos de su propio club.
 */
@Getter
public class CustomUserDetails extends User {

    private final Long userId;
    private final Role role;
    private final Long courseId;
    private final String courseName;

    public CustomUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities,
                              Long userId, Role role, Long courseId, String courseName) {
        super(username, password, authorities);
        this.userId = userId;
        this.role = role;
        this.courseId = courseId;
        this.courseName = courseName;
    }

    public boolean isSuperAdmin() {
        return role == Role.ADMIN;
    }
}
