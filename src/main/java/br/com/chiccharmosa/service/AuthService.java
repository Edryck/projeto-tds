package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.AuthDto.LoginRequest;
import br.com.chiccharmosa.dto.AuthDto.MeResponse;
import br.com.chiccharmosa.dto.AuthDto.RefreshRequest;
import br.com.chiccharmosa.dto.AuthDto.TokenResponse;
import br.com.chiccharmosa.entity.Device;
import br.com.chiccharmosa.entity.RefreshToken;
import br.com.chiccharmosa.entity.User;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.DeviceRepository;
import br.com.chiccharmosa.repository.PermissionRepository;
import br.com.chiccharmosa.repository.RefreshTokenRepository;
import br.com.chiccharmosa.repository.UserRepository;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration REFRESH_VALIDITY = Duration.ofDays(7);
    private static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos";
    private static final String SESSION_EXPIRED = "Sessão expirada";

    private record Failures(int count, Instant windowStart) {
    }

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long accessTokenSeconds;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Failures> failuresByKey = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, DeviceRepository deviceRepository,
            RefreshTokenRepository refreshTokenRepository, PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder, JwtService jwtService,
            @Value("${app.jwt.access-token-seconds:900}") long accessTokenSeconds) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessTokenSeconds = accessTokenSeconds;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String clientIp) {
        String key = request.email().toLowerCase() + ":" + clientIp;
        if (isBlocked(key)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente mais tarde.");
        }
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email().toLowerCase()).orElse(null);
        if (user == null || !user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(key);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        failuresByKey.remove(key);
        Device device = findOrCreateDevice(user, request);
        return issueTokens(user, device);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(request.refreshToken())).orElse(null);
        Instant now = Instant.now();
        if (stored == null || stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(now)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
        }
        User user = stored.getUser();
        Device device = stored.getDevice();
        if (user.isDeleted() || !user.isActive() || device.isDeleted() || !device.isActive()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
        }
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);
        return issueTokens(user, device);
    }

    @Transactional
    public void logout() {
        refreshTokenRepository.revokeAllByDeviceId(CurrentUser.current().deviceId(), Instant.now());
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        UUID userId = CurrentUser.current().userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED));
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().getName(),
                permissionRepository.findCodesByUserId(userId));
    }

    private Device findOrCreateDevice(User user, LoginRequest request) {
        Device device = deviceRepository.findById(request.deviceId()).orElse(null);
        if (device == null) {
            device = new Device();
            device.setId(request.deviceId());
            device.setUser(user);
            device.setName(request.deviceName() == null || request.deviceName().isBlank()
                    ? "Dispositivo" : request.deviceName());
            return deviceRepository.save(device);
        }
        if (!device.getUser().getId().equals(user.getId()) || device.isDeleted()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Dispositivo inválido");
        }
        if (!device.isActive()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Dispositivo desativado");
        }
        return device;
    }

    private TokenResponse issueTokens(User user, Device device) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        RefreshToken stored = new RefreshToken();
        stored.setId(UUID.randomUUID());
        stored.setUser(user);
        stored.setDevice(device);
        stored.setTokenHash(hash(refreshToken));
        stored.setExpiresAt(Instant.now().plus(REFRESH_VALIDITY));
        refreshTokenRepository.save(stored);

        String accessToken = jwtService.createAccessToken(user.getId(), device.getId());
        return new TokenResponse(accessToken, refreshToken, accessTokenSeconds);
    }

    private boolean isBlocked(String key) {
        Failures failures = failuresByKey.get(key);
        if (failures == null) {
            return false;
        }
        if (failures.windowStart().plus(FAILURE_WINDOW).isBefore(Instant.now())) {
            failuresByKey.remove(key, failures);
            return false;
        }
        return failures.count() >= MAX_FAILURES;
    }

    private void registerFailure(String key) {
        Instant now = Instant.now();
        failuresByKey.compute(key, (k, current) -> {
            if (current == null || current.windowStart().plus(FAILURE_WINDOW).isBefore(now)) {
                return new Failures(1, now);
            }
            return new Failures(current.count() + 1, current.windowStart());
        });
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
