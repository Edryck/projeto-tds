package br.com.chiccharmosa.service;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.StoreSettingsDto.Logo;
import br.com.chiccharmosa.dto.StoreSettingsDto.SettingsRequest;
import br.com.chiccharmosa.dto.StoreSettingsDto.SettingsResponse;
import br.com.chiccharmosa.entity.StoreSettings;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.StoreSettingsRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreSettingsService {

    public static final String INVALID_LOGO_MESSAGE = "Logo deve ser PNG, JPG ou WebP de até 1 MB";
    private static final int MAX_LOGO_BYTES = 1024 * 1024;

    private final StoreSettingsRepository storeSettingsRepository;
    private final AuditService auditService;

    public StoreSettingsService(StoreSettingsRepository storeSettingsRepository, AuditService auditService) {
        this.storeSettingsRepository = storeSettingsRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return SettingsResponse.from(find());
    }

    @Transactional
    public SettingsResponse update(SettingsRequest request, Instant changedAt) {
        StoreSettings settings = find();
        SettingsResponse before = SettingsResponse.from(settings);
        settings.setName(request.name());
        settings.setPhone(request.phone());
        settings.setEmail(request.email());
        settings.setAddress(request.address());
        settings.touch(changedAt);
        SettingsResponse after = SettingsResponse.from(settings);
        auditService.record(AuditService.STORE_SETTINGS, settings.getId(), AuditService.UPDATE, before, after,
                changedAt);
        return after;
    }

    @Transactional
    public SettingsResponse updateLogo(byte[] content, String contentType, Instant changedAt) {
        if (!isValidLogo(content, contentType)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_LOGO_MESSAGE);
        }
        StoreSettings settings = find();
        SettingsResponse before = SettingsResponse.from(settings);
        settings.setLogo(content);
        settings.setLogoContentType(contentType);
        settings.touch(changedAt);
        SettingsResponse after = SettingsResponse.from(settings);
        auditService.record(AuditService.STORE_SETTINGS, settings.getId(), AuditService.UPDATE, before, after,
                changedAt);
        return after;
    }

    @Transactional(readOnly = true)
    public Optional<Logo> logo() {
        StoreSettings settings = find();
        if (settings.getLogo() == null) {
            return Optional.empty();
        }
        return Optional.of(new Logo(settings.getLogo(), settings.getLogoContentType()));
    }

    private StoreSettings find() {
        return storeSettingsRepository.findById(SeedIds.STORE_SETTINGS)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Configurações da loja não encontradas"));
    }

    private boolean isValidLogo(byte[] content, String contentType) {
        if (content == null || content.length == 0 || content.length > MAX_LOGO_BYTES || contentType == null) {
            return false;
        }
        return switch (contentType) {
            case "image/png" -> startsWith(content, 0, 0x89, 0x50, 0x4E, 0x47);
            case "image/jpeg" -> startsWith(content, 0, 0xFF, 0xD8, 0xFF);
            case "image/webp" -> content.length >= 12 && startsWith(content, 0, 'R', 'I', 'F', 'F')
                    && startsWith(content, 8, 'W', 'E', 'B', 'P');
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, int offset, int... expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
