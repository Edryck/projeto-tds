package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.StockDto.EntryItem;
import br.com.chiccharmosa.dto.StockDto.MovementResponse;
import br.com.chiccharmosa.dto.StockDto.StockEntryRequest;
import br.com.chiccharmosa.dto.StockDto.StockEntryResponse;
import br.com.chiccharmosa.dto.SupplierDto.SupplierRequest;
import br.com.chiccharmosa.dto.SupplierDto.SupplierResponse;
import br.com.chiccharmosa.entity.MovementType;
import br.com.chiccharmosa.entity.ProductVariant;
import br.com.chiccharmosa.entity.StockEntry;
import br.com.chiccharmosa.entity.StockEntryItem;
import br.com.chiccharmosa.entity.StockMovement;
import br.com.chiccharmosa.entity.Supplier;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.ProductVariantRepository;
import br.com.chiccharmosa.repository.StockEntryRepository;
import br.com.chiccharmosa.repository.StockMovementRepository;
import br.com.chiccharmosa.repository.SupplierRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockMovementRepository movementRepository;
    private final StockEntryRepository entryRepository;
    private final ProductVariantRepository variantRepository;
    private final SupplierRepository supplierRepository;
    private final AuditService auditService;

    public StockService(StockMovementRepository movementRepository, StockEntryRepository entryRepository,
            ProductVariantRepository variantRepository, SupplierRepository supplierRepository,
            AuditService auditService) {
        this.movementRepository = movementRepository;
        this.entryRepository = entryRepository;
        this.variantRepository = variantRepository;
        this.supplierRepository = supplierRepository;
        this.auditService = auditService;
    }

    public Map<UUID, Integer> balances(Collection<UUID> variantIds) {
        Map<UUID, Integer> balances = new HashMap<>();
        if (variantIds.isEmpty()) {
            return balances;
        }
        for (UUID variantId : variantIds) {
            balances.put(variantId, 0);
        }
        List<Object[]> rows = movementRepository.sumByVariantIds(variantIds);
        for (Object[] row : rows) {
            balances.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return balances;
    }

    public int balance(UUID variantId) {
        return balances(List.of(variantId)).get(variantId);
    }

    public boolean entryExists(UUID id) {
        return entryRepository.existsById(id);
    }

    @Transactional
    public StockEntryResponse registerEntry(StockEntryRequest request, Instant changedAt) {
        if (request.id() != null) {
            StockEntry existing = entryRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return StockEntryResponse.from(existing);
            }
        }
        if (request.supplierId() != null
                && supplierRepository.findByIdAndDeletedAtIsNull(request.supplierId()).isEmpty()) {
            throw new BusinessException(HttpStatus.valueOf(422), "Fornecedor inexistente");
        }
        StockEntry entry = new StockEntry();
        entry.setId(request.id() == null ? UUID.randomUUID() : request.id());
        entry.setSupplierId(request.supplierId());
        entry.setEntryDate(request.entryDate());
        entry.setNote(request.note());
        entry.touch(changedAt);
        for (EntryItem requestItem : request.items()) {
            if (requestItem.quantity() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantidade deve ser maior que zero");
            }
            ProductVariant variant = variantRepository.findByIdAndDeletedAtIsNull(requestItem.variantId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Variante não encontrada"));
            StockEntryItem item = new StockEntryItem();
            item.setId(requestItem.id() == null ? UUID.randomUUID() : requestItem.id());
            item.setVariant(variant);
            item.setQuantity(requestItem.quantity());
            item.setUnitCost(requestItem.unitCost());
            item.touch(changedAt);
            entry.addItem(item);
        }
        entryRepository.save(entry);
        for (StockEntryItem item : entry.getItems()) {
            saveMovement(item.getVariant(), item.getQuantity(), MovementType.ENTRY, entry.getId(), null, changedAt);
        }
        StockEntryResponse response = StockEntryResponse.from(entry);
        auditService.record(AuditService.STOCK_ENTRY, entry.getId(), AuditService.CREATE, null, response, changedAt);
        return response;
    }

    public StockEntryResponse findEntry(UUID id) {
        StockEntry entry = entryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Entrada não encontrada"));
        return StockEntryResponse.from(entry);
    }

    public Page<StockEntryResponse> listEntries(Pageable pageable) {
        return entryRepository.findByDeletedAtIsNull(pageable).map(StockEntryResponse::from);
    }

    public Page<MovementResponse> listMovements(UUID variantId, Pageable pageable) {
        if (!variantRepository.existsById(variantId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Variante não encontrada");
        }
        return movementRepository.findByVariantIdOrderByOccurredAtDesc(variantId, pageable)
                .map(MovementResponse::from);
    }

    @Transactional
    public MovementResponse adjust(UUID variantId, int signedQuantity, String reason, Instant changedAt) {
        if (signedQuantity == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantidade do ajuste não pode ser zero");
        }
        ProductVariant variant = lockVariant(variantId);
        if (variant.isDeleted()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Variante não encontrada");
        }
        if (balance(variantId) + signedQuantity < 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Estoque insuficiente");
        }
        StockMovement movement = saveMovement(variant, signedQuantity, MovementType.ADJUSTMENT, null, reason,
                changedAt);
        MovementResponse response = MovementResponse.from(movement);
        auditService.record(AuditService.PRODUCT_VARIANT, variantId, AuditService.STOCK_ADJUSTMENT, null, response, changedAt);
        return response;
    }

    @Transactional
    public void withdraw(UUID variantId, int quantity, UUID referenceId, boolean allowNegative) {
        if (quantity <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Quantidade deve ser maior que zero");
        }
        ProductVariant variant = lockVariant(variantId);
        if (!allowNegative && balance(variantId) < quantity) {
            throw new BusinessException(HttpStatus.CONFLICT, "Estoque insuficiente");
        }
        saveMovement(variant, -quantity, MovementType.SALE, referenceId, null, Instant.now());
    }

    @Transactional
    public void reverse(UUID referenceId) {
        if (movementRepository.countByReferenceIdAndType(referenceId, MovementType.REVERSAL) > 0) {
            return;
        }
        Instant now = Instant.now();
        List<StockMovement> sales = movementRepository.findByReferenceIdAndType(referenceId, MovementType.SALE);
        for (StockMovement sale : sales) {
            saveMovement(sale.getVariant(), -sale.getQuantity(), MovementType.REVERSAL, referenceId, null, now);
        }
    }

    public boolean supplierExists(UUID id) {
        return supplierRepository.existsById(id);
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request, Instant changedAt) {
        if (request.id() != null) {
            Supplier existing = supplierRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return SupplierResponse.from(existing);
            }
        }
        Supplier supplier = new Supplier();
        supplier.setId(request.id() == null ? UUID.randomUUID() : request.id());
        supplier.setName(request.name());
        supplier.setPhone(request.phone());
        supplier.touch(changedAt);
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    public SupplierResponse getSupplier(UUID id) {
        return SupplierResponse.from(findActiveSupplier(id));
    }

    public Page<SupplierResponse> listSuppliers(String name, Pageable pageable) {
        if (name == null || name.isBlank()) {
            return supplierRepository.findByDeletedAtIsNull(pageable).map(SupplierResponse::from);
        }
        return supplierRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
                .map(SupplierResponse::from);
    }

    @Transactional
    public SupplierResponse updateSupplier(UUID id, SupplierRequest request, Instant changedAt) {
        Supplier supplier = findActiveSupplier(id);
        supplier.setName(request.name());
        supplier.setPhone(request.phone());
        supplier.touch(changedAt);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void deleteSupplier(UUID id, Instant changedAt) {
        findActiveSupplier(id).markDeleted(changedAt);
    }

    private Supplier findActiveSupplier(UUID id) {
        return supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Fornecedor não encontrado"));
    }

    private ProductVariant lockVariant(UUID variantId) {
        return variantRepository.findLockedById(variantId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Variante não encontrada"));
    }

    private StockMovement saveMovement(ProductVariant variant, int quantity, MovementType type, UUID referenceId,
            String reason, Instant occurredAt) {
        StockMovement movement = new StockMovement();
        movement.setId(UUID.randomUUID());
        movement.setVariant(variant);
        movement.setQuantity(quantity);
        movement.setType(type);
        movement.setReferenceId(referenceId);
        movement.setReason(reason);
        movement.setOccurredAt(occurredAt);
        movement.touch(occurredAt);
        return movementRepository.save(movement);
    }
}
