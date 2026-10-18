package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.AuditDto.AuditResponse;
import br.com.chiccharmosa.entity.AuditLog;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.AuditLogRepository;
import br.com.chiccharmosa.security.CurrentUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

    public static final String CATEGORY = "CATEGORY";
    public static final String PRODUCT = "PRODUCT";
    public static final String PRODUCT_VARIANT = "PRODUCT_VARIANT";
    public static final String STOCK_ENTRY = "STOCK_ENTRY";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String ORDER = "ORDER";
    public static final String FINANCIAL_ENTRY = "FINANCIAL_ENTRY";
    public static final String USER = "USER";
    public static final String ROLE = "ROLE";

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";
    public static final String STOCK_DIVERGENCE = "STOCK_DIVERGENCE";
    public static final String INCOME_DATE_MOVED = "INCOME_DATE_MOVED";

    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;

    public AuditService(AuditLogRepository auditLogRepository, JsonMapper jsonMapper) {
        this.auditLogRepository = auditLogRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void record(String entity, UUID entityId, String action, Object before, Object after, Instant actedAt) {
        CurrentUser user = CurrentUser.current();
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setUserId(user.userId());
        log.setDeviceId(user.deviceId());
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setBeforeData(before == null ? null : jsonMapper.writeValueAsString(before));
        log.setAfterData(after == null ? null : jsonMapper.writeValueAsString(after));
        log.setActedAt(actedAt);
        log.setSyncedAt(Instant.now());
        log.touch(actedAt);
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> list(String entity, UUID entityId, UUID userId, String action, Instant from,
            Instant to, Pageable pageable) {
        // postgres não descobre o tipo de um Instant nulo na query, então troco por limites bem largos
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? FAR_FUTURE : to;
        return auditLogRepository.search(entity, entityId, userId, action, start, end, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AuditResponse get(UUID id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Registro de auditoria não encontrado"));
        return toResponse(log);
    }

    private AuditResponse toResponse(AuditLog log) {
        return AuditResponse.from(log, parse(log.getBeforeData()), parse(log.getAfterData()));
    }

    private JsonNode parse(String json) {
        return json == null ? null : jsonMapper.readTree(json);
    }
}
