package com.golf.tournament.model;

import java.util.Set;

public enum Role {

    ADMIN(Set.of(Permission.TOTAL)),
    USER(Set.of(Permission.GAMES));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
