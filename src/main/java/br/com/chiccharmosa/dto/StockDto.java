package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.MovementType;
import br.com.chiccharmosa.entity.StockEntry;
import br.com.chiccharmosa.entity.StockEntryItem;
import br.com.chiccharmosa.entity.StockMovement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class StockDto {

    private StockDto() {
    }

    public record StockEntryRequest(UUID id, UUID supplierId, @NotNull LocalDate entryDate, String note,
            @NotEmpty List<@Valid EntryItem> items) {
    }

    public record EntryItem(UUID id, @NotNull UUID variantId, @Positive int quantity,
            @NotNull @PositiveOrZero BigDecimal unitCost) {
    }

    @Relation(collectionRelation = "stockEntries")
    public record StockEntryResponse(UUID id, UUID supplierId, LocalDate entryDate, String note,
            List<EntryItem> items) {

        public static StockEntryResponse from(StockEntry entry) {
            List<EntryItem> items = new ArrayList<>();
            for (StockEntryItem item : entry.getItems()) {
                items.add(new EntryItem(item.getId(), item.getVariant().getId(), item.getQuantity(),
                        item.getUnitCost()));
            }
            return new StockEntryResponse(entry.getId(), entry.getSupplierId(), entry.getEntryDate(),
                    entry.getNote(), items);
        }
    }

    @Relation(collectionRelation = "movements")
    public record MovementResponse(UUID id, UUID variantId, int quantity, MovementType type, UUID referenceId,
            String reason, Instant occurredAt) {

        public static MovementResponse from(StockMovement movement) {
            return new MovementResponse(movement.getId(), movement.getVariant().getId(), movement.getQuantity(),
                    movement.getType(), movement.getReferenceId(), movement.getReason(), movement.getOccurredAt());
        }
    }

    public record AdjustmentRequest(int quantity, @NotBlank String reason) {
    }
}
