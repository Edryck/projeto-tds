package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;
import tools.jackson.databind.JsonNode;

public final class AuditDto {

    private AuditDto() {
    }

    @Relation(collectionRelation = "auditLogs")
    public record AuditResponse(UUID id, UUID userId, UUID deviceId, String entity, UUID entityId, String action,
            JsonNode before, JsonNode after, Instant actedAt, Instant syncedAt) {

        public static AuditResponse from(AuditLog log, JsonNode before, JsonNode after) {
            return new AuditResponse(log.getId(), log.getUserId(), log.getDeviceId(), log.getEntity(),
                    log.getEntityId(), log.getAction(), before, after, log.getActedAt(), log.getSyncedAt());
        }
    }
}
