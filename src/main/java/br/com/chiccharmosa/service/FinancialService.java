package br.com.chiccharmosa.service;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.FinanceDto.AccountResponse;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingRequest;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingResponse;
import br.com.chiccharmosa.dto.FinanceDto.EntryRequest;
import br.com.chiccharmosa.dto.FinanceDto.EntryResponse;
import br.com.chiccharmosa.dto.FinanceDto.FinancialCategoryRequest;
import br.com.chiccharmosa.dto.FinanceDto.FinancialCategoryResponse;
import br.com.chiccharmosa.dto.FinanceDto.ProfitGroup;
import br.com.chiccharmosa.dto.FinanceDto.ProfitLine;
import br.com.chiccharmosa.dto.FinanceDto.ProfitResponse;
import br.com.chiccharmosa.entity.CashClosing;
import br.com.chiccharmosa.entity.EntryType;
import br.com.chiccharmosa.entity.FinancialAccount;
import br.com.chiccharmosa.entity.FinancialCategory;
import br.com.chiccharmosa.entity.FinancialEntry;
import br.com.chiccharmosa.entity.Order;
import br.com.chiccharmosa.entity.PaymentMethod;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.CashClosingRepository;
import br.com.chiccharmosa.repository.FinancialAccountRepository;
import br.com.chiccharmosa.repository.FinancialCategoryRepository;
import br.com.chiccharmosa.repository.FinancialEntryRepository;
import br.com.chiccharmosa.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinancialService {

    public static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate FAR_PAST = LocalDate.of(1970, 1, 1);
    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private final FinancialAccountRepository accountRepository;
    private final FinancialCategoryRepository categoryRepository;
    private final FinancialEntryRepository entryRepository;
    private final CashClosingRepository closingRepository;
    private final PaymentRepository paymentRepository;

    public FinancialService(FinancialAccountRepository accountRepository,
            FinancialCategoryRepository categoryRepository, FinancialEntryRepository entryRepository,
            CashClosingRepository closingRepository, PaymentRepository paymentRepository) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.entryRepository = entryRepository;
        this.closingRepository = closingRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<AccountResponse> listAccounts() {
        List<AccountResponse> accounts = new ArrayList<>();
        for (FinancialAccount account : accountRepository.findByDeletedAtIsNullOrderByName()) {
            accounts.add(AccountResponse.from(account));
        }
        return accounts;
    }

    public boolean categoryExists(UUID id) {
        return categoryRepository.existsById(id);
    }

    public Page<FinancialCategoryResponse> listCategories(Pageable pageable) {
        return categoryRepository.findByDeletedAtIsNull(pageable).map(FinancialCategoryResponse::from);
    }

    @Transactional
    public FinancialCategoryResponse createCategory(FinancialCategoryRequest request, Instant changedAt) {
        if (request.id() != null) {
            FinancialCategory existing = categoryRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return FinancialCategoryResponse.from(existing);
            }
        }
        FinancialCategory category = new FinancialCategory();
        category.setId(request.id() == null ? UUID.randomUUID() : request.id());
        category.setName(request.name());
        category.setType(request.type());
        category.touch(changedAt);
        return FinancialCategoryResponse.from(categoryRepository.save(category));
    }

    public boolean entryExists(UUID id) {
        return entryRepository.existsById(id);
    }

    @Transactional
    public EntryResponse create(EntryRequest request, Instant changedAt) {
        if (request.id() != null) {
            FinancialEntry existing = entryRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return EntryResponse.from(existing);
            }
        }
        FinancialEntry entry = new FinancialEntry();
        entry.setId(request.id() == null ? UUID.randomUUID() : request.id());
        applyRequest(entry, request);
        ensureDayOpen(entry.getAccountId(), entry.getEntryDate());
        entry.touch(changedAt);
        return EntryResponse.from(entryRepository.save(entry));
    }

    public EntryResponse get(UUID id) {
        return EntryResponse.from(findActiveEntry(id));
    }

    public Page<EntryResponse> list(UUID accountId, EntryType type, LocalDate from, LocalDate to,
            Pageable pageable) {
        // postgres não descobre o tipo de uma data nula na query, então troco por limites bem largos
        LocalDate start = from == null ? FAR_PAST : from;
        LocalDate end = to == null ? FAR_FUTURE : to;
        return entryRepository.search(accountId, type, start, end, pageable).map(EntryResponse::from);
    }

    @Transactional
    public EntryResponse update(UUID id, EntryRequest request, Instant changedAt) {
        FinancialEntry entry = findEditableEntry(id);
        ensureDayOpen(entry.getAccountId(), entry.getEntryDate());
        applyRequest(entry, request);
        ensureDayOpen(entry.getAccountId(), entry.getEntryDate());
        entry.touch(changedAt);
        return EntryResponse.from(entry);
    }

    @Transactional
    public void delete(UUID id, Instant changedAt) {
        FinancialEntry entry = findEditableEntry(id);
        ensureDayOpen(entry.getAccountId(), entry.getEntryDate());
        entry.markDeleted(changedAt);
    }

    public boolean closingExists(UUID id) {
        return closingRepository.existsById(id);
    }

    @Transactional
    public CashClosingResponse close(CashClosingRequest request) {
        if (request.id() != null) {
            CashClosing existing = closingRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return CashClosingResponse.from(existing);
            }
        }
        if (!accountRepository.existsByIdAndDeletedAtIsNull(request.accountId())) {
            throw new BusinessException(HttpStatus.valueOf(422), "Conta inexistente");
        }
        if (closingRepository.existsByAccountIdAndClosingDate(request.accountId(), request.closingDate())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Caixa já fechado neste dia");
        }
        UUID accountId = request.accountId();
        LocalDate day = request.closingDate();
        CashClosing closing = new CashClosing();
        closing.setId(request.id() == null ? UUID.randomUUID() : request.id());
        closing.setAccountId(accountId);
        closing.setClosingDate(day);
        closing.setPixTotal(money(entryRepository.sumOfDay(accountId, day, EntryType.INCOME, PaymentMethod.PIX)));
        closing.setCardTotal(money(entryRepository.sumOfDay(accountId, day, EntryType.INCOME, PaymentMethod.CARD)));
        closing.setCashTotal(money(entryRepository.sumOfDay(accountId, day, EntryType.INCOME, PaymentMethod.CASH)));
        closing.setExpenseTotal(money(entryRepository.sumOfDay(accountId, day, EntryType.EXPENSE, null)));
        return CashClosingResponse.from(closingRepository.save(closing));
    }

    public CashClosingResponse getClosing(UUID id) {
        CashClosing closing = closingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Fechamento não encontrado"));
        return CashClosingResponse.from(closing);
    }

    public Page<CashClosingResponse> listClosings(UUID accountId, LocalDate from, LocalDate to, Pageable pageable) {
        LocalDate start = from == null ? FAR_PAST : from;
        LocalDate end = to == null ? FAR_FUTURE : to;
        return closingRepository.search(accountId, start, end, pageable).map(CashClosingResponse::from);
    }

    public ProfitResponse profit(UUID accountId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período obrigatório");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período inválido");
        }
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal costOfGoods = BigDecimal.ZERO;
        if (SeedIds.STORE_ACCOUNT.equals(accountId)) {
            Instant start = from.atStartOfDay(STORE_ZONE).toInstant();
            Instant end = to.plusDays(1).atStartOfDay(STORE_ZONE).toInstant();
            revenue = paymentRepository.sumRevenue(start, end);
            costOfGoods = paymentRepository.sumCostOfGoods(start, end);
        }
        // estorno desfaz uma venda que já não entra na receita, e frete é só repasse
        List<UUID> ignoredCategories = List.of(SeedIds.REFUND_CATEGORY, SeedIds.SHIPPING_CATEGORY);
        BigDecimal expenses = entryRepository.sumOfPeriod(accountId, EntryType.EXPENSE, from, to, ignoredCategories);
        BigDecimal profit = revenue.subtract(costOfGoods).subtract(expenses);
        return new ProfitResponse(money(revenue), money(costOfGoods), money(expenses), money(profit));
    }

    public List<ProfitLine> profitBreakdown(UUID accountId, LocalDate from, LocalDate to, String groupBy) {
        if (from == null || to == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período obrigatório");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período inválido");
        }
        ProfitGroup group = parseGroup(groupBy);
        if (!SeedIds.STORE_ACCOUNT.equals(accountId)) {
            return new ArrayList<>();
        }
        Instant start = from.atStartOfDay(STORE_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(STORE_ZONE).toInstant();
        if (group == ProfitGroup.DAY) {
            return breakdownByDay(start, end);
        }
        List<Object[]> rows = group == ProfitGroup.PRODUCT
                ? paymentRepository.profitByProduct(start, end)
                : paymentRepository.profitByCategory(start, end);
        List<ProfitLine> lines = new ArrayList<>();
        for (Object[] row : rows) {
            lines.add(profitLine((String) row[0], (BigDecimal) row[1], (BigDecimal) row[2]));
        }
        return lines;
    }

    private List<ProfitLine> breakdownByDay(Instant start, Instant end) {
        Map<String, BigDecimal[]> totalsByDay = new TreeMap<>();
        for (Object[] row : paymentRepository.profitByOrder(start, end)) {
            String day = ((Instant) row[0]).atZone(STORE_ZONE).toLocalDate().toString();
            BigDecimal[] totals = totalsByDay.computeIfAbsent(day, key -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add((BigDecimal) row[1]);
            totals[1] = totals[1].add((BigDecimal) row[2]);
        }
        List<ProfitLine> lines = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : totalsByDay.entrySet()) {
            lines.add(profitLine(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        return lines;
    }

    private ProfitLine profitLine(String key, BigDecimal revenue, BigDecimal costOfGoods) {
        return new ProfitLine(key, money(revenue), money(costOfGoods), money(revenue.subtract(costOfGoods)));
    }

    private ProfitGroup parseGroup(String groupBy) {
        try {
            return ProfitGroup.valueOf(groupBy);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Agrupamento inválido");
        }
    }

    public List<EntryResponse> findByOrder(UUID orderId) {
        List<EntryResponse> entries = new ArrayList<>();
        for (FinancialEntry entry : entryRepository.findByOrderId(orderId)) {
            if (!entry.isDeleted()) {
                entries.add(EntryResponse.from(entry));
            }
        }
        return entries;
    }

    @Transactional
    public EntryResponse recordIncome(Order order, PaymentMethod method, Instant paidAt) {
        FinancialEntry existing = findOrderEntry(order.getId(), EntryType.INCOME);
        if (existing != null) {
            return EntryResponse.from(existing);
        }
        FinancialEntry entry = newOrderEntry(order, EntryType.INCOME, SeedIds.SALES_CATEGORY, paidAt);
        entry.setPaymentMethod(method);
        entry.setDescription("Venda do pedido");
        return EntryResponse.from(entryRepository.save(entry));
    }

    @Transactional
    public EntryResponse recordRefund(Order order, Instant cancelledAt) {
        FinancialEntry existing = findOrderEntry(order.getId(), EntryType.EXPENSE);
        if (existing != null) {
            return EntryResponse.from(existing);
        }
        FinancialEntry entry = newOrderEntry(order, EntryType.EXPENSE, SeedIds.REFUND_CATEGORY, cancelledAt);
        entry.setDescription("Estorno do pedido");
        return EntryResponse.from(entryRepository.save(entry));
    }

    private FinancialEntry newOrderEntry(Order order, EntryType type, UUID categoryId, Instant happenedAt) {
        FinancialEntry entry = new FinancialEntry();
        entry.setId(UUID.randomUUID());
        entry.setAccountId(SeedIds.STORE_ACCOUNT);
        entry.setCategoryId(categoryId);
        entry.setType(type);
        entry.setAmount(order.getTotal().setScale(2, RoundingMode.HALF_UP));
        entry.setEntryDate(entryDateFor(SeedIds.STORE_ACCOUNT, happenedAt));
        entry.setOrderId(order.getId());
        entry.touch(happenedAt);
        return entry;
    }

    private LocalDate entryDateFor(UUID accountId, Instant happenedAt) {
        LocalDate day = happenedAt.atZone(STORE_ZONE).toLocalDate();
        // fechamento não muda nunca, então a venda de dia fechado cai no caixa de hoje
        if (closingRepository.existsByAccountIdAndClosingDate(accountId, day)) {
            return LocalDate.now(STORE_ZONE);
        }
        return day;
    }

    private void ensureDayOpen(UUID accountId, LocalDate day) {
        if (closingRepository.existsByAccountIdAndClosingDate(accountId, day)) {
            throw new BusinessException(HttpStatus.valueOf(422), "Caixa do dia já fechado");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private FinancialEntry findOrderEntry(UUID orderId, EntryType type) {
        for (FinancialEntry entry : entryRepository.findByOrderId(orderId)) {
            if (!entry.isDeleted() && entry.getType() == type) {
                return entry;
            }
        }
        return null;
    }

    private void applyRequest(FinancialEntry entry, EntryRequest request) {
        if (!accountRepository.existsByIdAndDeletedAtIsNull(request.accountId())) {
            throw new BusinessException(HttpStatus.valueOf(422), "Conta inexistente");
        }
        FinancialCategory category = categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                .orElseThrow(() -> new BusinessException(HttpStatus.valueOf(422), "Categoria inexistente"));
        if (category.getType() != request.type()) {
            throw new BusinessException(HttpStatus.valueOf(422), "Tipo do lançamento diferente do tipo da categoria");
        }
        entry.setAccountId(request.accountId());
        entry.setCategoryId(request.categoryId());
        entry.setType(request.type());
        entry.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        entry.setPaymentMethod(request.paymentMethod());
        entry.setEntryDate(request.entryDate());
        entry.setDescription(request.description());
    }

    private FinancialEntry findActiveEntry(UUID id) {
        return entryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Lançamento não encontrado"));
    }

    private FinancialEntry findEditableEntry(UUID id) {
        FinancialEntry entry = findActiveEntry(id);
        if (entry.getOrderId() != null) {
            throw new BusinessException(HttpStatus.valueOf(422), "Lançamento de pedido não pode ser alterado");
        }
        return entry;
    }
}
