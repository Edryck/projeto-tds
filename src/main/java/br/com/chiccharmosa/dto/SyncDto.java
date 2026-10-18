package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.entity.SyncAction;
import br.com.chiccharmosa.entity.SyncEntity;
import br.com.chiccharmosa.entity.SyncStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class SyncDto {

    private SyncDto() {
    }

    public record PushRequest(@NotEmpty List<@Valid Operation> operations) {
    }

    public record Operation(@NotNull UUID id, @NotNull SyncEntity entity, @NotNull SyncAction action,
            @NotNull UUID entityId, JsonNode payload, @NotNull Instant clientTime) {
    }

    public record PushResponse(List<Result> results) {
    }

    public record Result(UUID id, SyncStatus status, String reason) {
    }

    public record VariantCreatePayload(@NotNull UUID productId, @NotNull @Valid VariantRequest variant) {
    }

    public record PullResponse(Instant serverTime, Instant nextSince, UUID nextAfterId, boolean hasMore,
            List<Item> items) {
    }

    public record Item(SyncEntity entity, UUID id, boolean deleted, Object data, Instant updatedAt) {
    }
}
