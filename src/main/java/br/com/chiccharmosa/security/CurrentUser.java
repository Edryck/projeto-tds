package br.com.chiccharmosa.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public record CurrentUser(UUID userId, UUID deviceId, Set<String> permissions) {

    public UsernamePasswordAuthenticationToken authentication() {
        List<GrantedAuthority> authorities = permissions.stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toList();
        return new UsernamePasswordAuthenticationToken(this, null, authorities);
    }

    public static CurrentUser current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new IllegalStateException("Nenhum usuário autenticado");
    }
}
