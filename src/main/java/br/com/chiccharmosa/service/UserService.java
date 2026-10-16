package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.UserDto.DeviceResponse;
import br.com.chiccharmosa.dto.UserDto.UserRequest;
import br.com.chiccharmosa.dto.UserDto.UserResponse;
import br.com.chiccharmosa.entity.Device;
import br.com.chiccharmosa.entity.Role;
import br.com.chiccharmosa.entity.User;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.DeviceRepository;
import br.com.chiccharmosa.repository.RefreshTokenRepository;
import br.com.chiccharmosa.repository.RoleRepository;
import br.com.chiccharmosa.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, DeviceRepository deviceRepository,
            RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.deviceRepository = deviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        return userRepository.existsById(id);
    }

    @Transactional
    public UserResponse create(UserRequest request, Instant changedAt) {
        if (request.id() != null) {
            User existing = userRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return UserResponse.from(existing);
            }
        }
        if (request.password() == null || request.password().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Senha obrigatória");
        }
        String email = normalizeEmail(request.email());
        ensureEmailFree(email, null);
        User user = new User();
        user.setId(request.id() == null ? UUID.randomUUID() : request.id());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole(findRole(request.roleId()));
        user.setActive(request.active());
        user.touch(changedAt);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(findActive(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String name, Pageable pageable) {
        if (name == null || name.isBlank()) {
            return userRepository.findByDeletedAtIsNull(pageable).map(UserResponse::from);
        }
        return userRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
                .map(UserResponse::from);
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request, Instant changedAt) {
        User user = findActive(id);
        String email = normalizeEmail(request.email());
        ensureEmailFree(email, id);
        user.setEmail(email);
        user.setName(request.name());
        user.setRole(findRole(request.roleId()));
        user.setActive(request.active());
        if (request.password() != null && !request.password().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.touch(changedAt);
        userRepository.saveAndFlush(user);
        if (!request.active()) {
            refreshTokenRepository.revokeAllByUserId(id, changedAt);
        }
        return UserResponse.from(userRepository.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(UUID id, Instant changedAt) {
        User user = findActive(id);
        user.markDeleted(changedAt);
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllByUserId(id, changedAt);
    }

    @Transactional(readOnly = true)
    public Page<DeviceResponse> listDevices(UUID userId, Pageable pageable) {
        if (userId == null) {
            return deviceRepository.findByDeletedAtIsNull(pageable).map(DeviceResponse::from);
        }
        return deviceRepository.findByDeletedAtIsNullAndUserId(userId, pageable).map(DeviceResponse::from);
    }

    @Transactional
    public void disableDevice(UUID id, Instant changedAt) {
        Device device = deviceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Dispositivo não encontrado"));
        device.setActive(false);
        device.touch(changedAt);
        deviceRepository.saveAndFlush(device);
        refreshTokenRepository.revokeAllByDeviceId(id, changedAt);
    }

    private User findActive(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private Role findRole(UUID roleId) {
        return roleRepository.findByIdAndDeletedAtIsNull(roleId)
                .orElseThrow(() -> new BusinessException(HttpStatus.valueOf(422), "Perfil inexistente"));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureEmailFree(String email, UUID ownId) {
        User other = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (other != null && !other.getId().equals(ownId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
    }
}
