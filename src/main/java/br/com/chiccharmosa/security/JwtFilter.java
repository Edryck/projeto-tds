package br.com.chiccharmosa.security;

import br.com.chiccharmosa.entity.Device;
import br.com.chiccharmosa.entity.User;
import br.com.chiccharmosa.repository.DeviceRepository;
import br.com.chiccharmosa.repository.PermissionRepository;
import br.com.chiccharmosa.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PermissionRepository permissionRepository;

    public JwtFilter(JwtService jwtService, UserRepository userRepository, DeviceRepository deviceRepository,
            PermissionRepository permissionRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<CurrentUser> currentUser = resolve(header.substring(BEARER.length()));
        if (currentUser.isEmpty()) {
            SecurityContextHolder.clearContext();
            writeProblem(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(currentUser.get().authentication());
        chain.doFilter(request, response);
    }

    private Optional<CurrentUser> resolve(String token) {
        JwtService.JwtClaims claims;
        try {
            claims = jwtService.parse(token);
        } catch (BadCredentialsException exception) {
            return Optional.empty();
        }
        User user = userRepository.findById(claims.userId()).orElse(null);
        if (user == null || user.isDeleted() || !user.isActive()) {
            return Optional.empty();
        }
        Device device = deviceRepository.findByIdAndUserId(claims.deviceId(), user.getId()).orElse(null);
        if (device == null || device.isDeleted() || !device.isActive()) {
            return Optional.empty();
        }
        Set<String> permissions = permissionRepository.findCodesByUserId(user.getId());
        return Optional.of(new CurrentUser(user.getId(), device.getId(), permissions));
    }

    public static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
                + status.value() + ",\"detail\":\"" + detail + "\"}";
        response.getWriter().write(body);
    }
}
