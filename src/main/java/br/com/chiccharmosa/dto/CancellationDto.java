package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.CancellationRequest;
import br.com.chiccharmosa.entity.CancellationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class CancellationDto {

    private CancellationDto() {
    }

    public record CancellationRequestBody(@NotBlank @Size(max = 500) String reason) {
    }

    public record DecisionBody(@Size(max = 500) String response) {
    }

    @Relation(collectionRelation = "cancellationRequests")
    public record CancellationView(UUID id, UUID orderId, String orderCustomerName, BigDecimal orderTotal,
            String reason, String requestedByName, Instant requestedAt, CancellationStatus status,
            String decidedByName, Instant decidedAt, String response) {

        public static CancellationView from(CancellationRequest request) {
            String decidedByName = request.getDecidedBy() == null ? null : request.getDecidedBy().getName();
            return new CancellationView(request.getId(), request.getOrder().getId(),
                    request.getOrder().getCustomer().getName(), request.getOrder().getTotal(), request.getReason(),
                    request.getRequestedBy().getName(), request.getCreatedAt(), request.getStatus(), decidedByName,
                    request.getDecidedAt(), request.getResponse());
        }
    }
}
