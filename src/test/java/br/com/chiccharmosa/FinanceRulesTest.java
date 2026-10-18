package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingRequest;
import br.com.chiccharmosa.dto.FinanceDto.CashClosingResponse;
import br.com.chiccharmosa.dto.FinanceDto.EntryRequest;
import br.com.chiccharmosa.dto.FinanceDto.EntryResponse;
import br.com.chiccharmosa.dto.FinanceDto.ProfitResponse;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.entity.EntryType;
import br.com.chiccharmosa.entity.PaymentMethod;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.PaymentRepository;
import br.com.chiccharmosa.service.CatalogService;
import br.com.chiccharmosa.service.FinancialService;
import br.com.chiccharmosa.service.OrderService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class FinanceRulesTest extends IntegrationTest {

    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private TestData testData;

    @Autowired
    private OrderService orderService;

    @Autowired
    private FinancialService financialService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CatalogService catalogService;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        testData.loginAs(testData.admin());
        customerId = testData.customer();
    }

    @Test
    void payingOrderCreatesOneIncomeInStoreAccount() {
        OrderResponse order = pendingOrder("100.00", "40.00", "15.00");

        orderService.pay(order.id(), PaymentMethod.PIX, Instant.now());

        List<EntryResponse> entries = financialService.findByOrder(order.id());
        assertThat(entries).hasSize(1);
        EntryResponse income = entries.get(0);
        assertThat(income.accountId()).isEqualTo(SeedIds.STORE_ACCOUNT);
        assertThat(income.categoryId()).isEqualTo(SeedIds.SALES_CATEGORY);
        assertThat(income.type()).isEqualTo(EntryType.INCOME);
        assertThat(income.amount()).isEqualByComparingTo("115.00");
        assertThat(income.paymentMethod()).isEqualTo(PaymentMethod.PIX);
        assertThat(income.entryDate()).isEqualTo(today());
        assertThat(paymentRepository.findByOrderId(order.id()).orElseThrow().getAmount()).isEqualByComparingTo("115.00");
    }

    @Test
    void payingTwiceCreatesOnlyOneIncome() {
        OrderResponse order = pendingOrder("100.00", "40.00", null);
        orderService.pay(order.id(), PaymentMethod.PIX, Instant.now());

        assertStatus(() -> orderService.pay(order.id(), PaymentMethod.PIX, Instant.now()), UNPROCESSABLE);

        assertThat(financialService.findByOrder(order.id())).hasSize(1);
    }

    @Test
    void cancellingPaidOrderCreatesRefundExpense() {
        OrderResponse order = pendingOrder("100.00", "40.00", "15.00");
        orderService.pay(order.id(), PaymentMethod.CASH, Instant.now());

        orderService.cancel(order.id(), Instant.now());

        EntryResponse refund = onlyEntry(order.id(), EntryType.EXPENSE);
        assertThat(refund.categoryId()).isEqualTo(SeedIds.REFUND_CATEGORY);
        assertThat(refund.amount()).isEqualByComparingTo("115.00");
    }

    @Test
    void cancellingPendingOrderCreatesNoEntry() {
        OrderResponse order = pendingOrder("100.00", "40.00", null);

        orderService.cancel(order.id(), Instant.now());

        assertThat(financialService.findByOrder(order.id())).isEmpty();
    }

    @Test
    void incomeFromOrderCannotBeEditedByEntryEndpoint() {
        OrderResponse order = pendingOrder("100.00", "40.00", null);
        orderService.pay(order.id(), PaymentMethod.PIX, Instant.now());
        EntryResponse income = onlyEntry(order.id(), EntryType.INCOME);
        EntryRequest change = new EntryRequest(income.id(), SeedIds.STORE_ACCOUNT, SeedIds.SALES_CATEGORY,
                EntryType.INCOME, new BigDecimal("1.00"), PaymentMethod.PIX, today(), "Alterado");

        assertStatus(() -> financialService.update(income.id(), change, Instant.now()), UNPROCESSABLE);
        assertStatus(() -> financialService.delete(income.id(), Instant.now()), UNPROCESSABLE);
    }

    @Test
    void incomeUsesPaymentDayWhenDayIsOpen() {
        LocalDate payDay = testData.uniqueDay();
        OrderResponse order = pendingOrder("100.00", "40.00", null);

        orderService.pay(order.id(), PaymentMethod.CARD, noonOf(payDay));

        assertThat(onlyEntry(order.id(), EntryType.INCOME).entryDate()).isEqualTo(payDay);
    }

    @Test
    void payingOrderOnClosedDayUsesServerToday() {
        LocalDate closedDay = testData.uniqueDay();
        CashClosingResponse closing = close(closedDay);
        OrderResponse order = pendingOrder("100.00", "40.00", null);

        orderService.pay(order.id(), PaymentMethod.PIX, noonOf(closedDay));

        assertThat(onlyEntry(order.id(), EntryType.INCOME).entryDate()).isEqualTo(today());
        CashClosingResponse reloaded = financialService.getClosing(closing.id());
        assertThat(reloaded.pixTotal()).isEqualByComparingTo(closing.pixTotal());
        assertThat(reloaded.balance()).isEqualByComparingTo(closing.balance());
    }

    @Test
    void refundOnClosedDayUsesServerToday() {
        LocalDate payDay = testData.uniqueDay();
        LocalDate cancelDay = testData.uniqueDay();
        OrderResponse order = pendingOrder("100.00", "40.00", null);
        orderService.pay(order.id(), PaymentMethod.PIX, noonOf(payDay));
        close(cancelDay);

        orderService.cancel(order.id(), noonOf(cancelDay));

        assertThat(onlyEntry(order.id(), EntryType.INCOME).entryDate()).isEqualTo(payDay);
        assertThat(onlyEntry(order.id(), EntryType.EXPENSE).entryDate()).isEqualTo(today());
    }

    @Test
    void manualEntryOnClosedDayStillReturns422() {
        LocalDate closedDay = testData.uniqueDay();
        close(closedDay);

        assertStatus(() -> entry(closedDay, EntryType.EXPENSE, SeedIds.OTHER_CATEGORY, null, "10.00"), UNPROCESSABLE);
    }

    @Test
    void closingSumsEntriesByMethod() {
        LocalDate day = testData.uniqueDay();
        entry(day, EntryType.INCOME, SeedIds.SALES_CATEGORY, PaymentMethod.PIX, "30.00");
        entry(day, EntryType.INCOME, SeedIds.SALES_CATEGORY, PaymentMethod.PIX, "20.00");
        entry(day, EntryType.INCOME, SeedIds.SALES_CATEGORY, PaymentMethod.CASH, "10.00");
        entry(day, EntryType.EXPENSE, SeedIds.OTHER_CATEGORY, null, "15.00");

        CashClosingResponse closing = close(day);

        assertThat(closing.pixTotal()).isEqualByComparingTo("50.00");
        assertThat(closing.cardTotal()).isEqualByComparingTo("0.00");
        assertThat(closing.cashTotal()).isEqualByComparingTo("10.00");
        assertThat(closing.expenseTotal()).isEqualByComparingTo("15.00");
        assertThat(closing.balance()).isEqualByComparingTo("45.00");
    }

    @Test
    void closingIgnoresOtherDays() {
        LocalDate day = testData.uniqueDay();
        LocalDate otherDay = testData.uniqueDay();
        entry(day, EntryType.INCOME, SeedIds.SALES_CATEGORY, PaymentMethod.PIX, "30.00");
        entry(otherDay, EntryType.INCOME, SeedIds.SALES_CATEGORY, PaymentMethod.PIX, "99.00");

        assertThat(close(day).pixTotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void secondClosingSameDayAndAccountReturns409() {
        LocalDate day = testData.uniqueDay();
        close(day);

        assertStatus(() -> close(day), HttpStatus.CONFLICT);
    }

    @Test
    void profitUsesCostCopiedAtSale() {
        LocalDate day = testData.uniqueDay();
        OrderResponse order = pendingOrder("100.00", "40.00", null);
        orderService.pay(order.id(), PaymentMethod.PIX, noonOf(day));
        entry(day, EntryType.EXPENSE, SeedIds.OTHER_CATEGORY, null, "10.00");

        ProfitResponse before = financialService.profit(SeedIds.STORE_ACCOUNT, day, day);

        UUID variantId = order.items().get(0).variantId();
        VariantRequest costlier = new VariantRequest(variantId, "Preto", "M", null, new BigDecimal("100.00"), new BigDecimal("70.00"));
        catalogService.updateVariant(variantId, costlier, Instant.now());
        ProfitResponse after = financialService.profit(SeedIds.STORE_ACCOUNT, day, day);

        assertThat(before.revenue()).isEqualByComparingTo("100.00");
        assertThat(before.costOfGoods()).isEqualByComparingTo("40.00");
        assertThat(before.expenses()).isEqualByComparingTo("10.00");
        assertThat(before.profit()).isEqualByComparingTo("50.00");
        assertThat(after.profit()).isEqualByComparingTo("50.00");
    }

    @Test
    void profitIgnoresCancelledOrdersAndRefundCategory() {
        LocalDate day = testData.uniqueDay();
        OrderResponse kept = pendingOrder("100.00", "40.00", null);
        OrderResponse cancelled = pendingOrder("100.00", "40.00", null);
        orderService.pay(kept.id(), PaymentMethod.PIX, noonOf(day));
        orderService.pay(cancelled.id(), PaymentMethod.PIX, noonOf(day));
        orderService.cancel(cancelled.id(), noonOf(day).plus(Duration.ofHours(1)));

        ProfitResponse profit = financialService.profit(SeedIds.STORE_ACCOUNT, day, day);

        assertThat(profit.revenue()).isEqualByComparingTo("100.00");
        assertThat(profit.costOfGoods()).isEqualByComparingTo("40.00");
        assertThat(profit.expenses()).isEqualByComparingTo("0.00");
        assertThat(profit.profit()).isEqualByComparingTo("60.00");
    }

    @Test
    void profitTreatsShippingAsPassThrough() {
        LocalDate day = testData.uniqueDay();
        OrderResponse order = pendingOrder("100.00", "40.00", "20.00");
        orderService.pay(order.id(), PaymentMethod.PIX, noonOf(day));
        entry(day, EntryType.EXPENSE, SeedIds.SHIPPING_CATEGORY, null, "20.00");
        entry(day, EntryType.EXPENSE, SeedIds.OTHER_CATEGORY, null, "10.00");

        ProfitResponse profit = financialService.profit(SeedIds.STORE_ACCOUNT, day, day);

        assertThat(onlyEntry(order.id(), EntryType.INCOME).amount()).isEqualByComparingTo("120.00");
        assertThat(profit.revenue()).isEqualByComparingTo("100.00");
        assertThat(profit.costOfGoods()).isEqualByComparingTo("40.00");
        assertThat(profit.expenses()).isEqualByComparingTo("10.00");
        assertThat(profit.profit()).isEqualByComparingTo("50.00");
    }

    @Test
    void invertedProfitPeriodIsRejected() {
        LocalDate day = testData.uniqueDay();

        assertStatus(() -> financialService.profit(SeedIds.STORE_ACCOUNT, day, day.minusDays(1)), HttpStatus.BAD_REQUEST);
    }

    private CashClosingResponse close(LocalDate day) {
        return financialService.close(new CashClosingRequest(UUID.randomUUID(), SeedIds.STORE_ACCOUNT, day));
    }

    private OrderResponse pendingOrder(String price, String cost, String shippingCost) {
        UUID variantId = testData.variantWithStock(price, cost, 5);
        return orderService.create(testData.orderRequest(customerId, variantId, 1, shippingCost, null), Instant.now(), false);
    }

    private EntryResponse entry(LocalDate day, EntryType type, UUID categoryId, PaymentMethod method, String amount) {
        EntryRequest request = new EntryRequest(UUID.randomUUID(), SeedIds.STORE_ACCOUNT, categoryId, type,
                new BigDecimal(amount), method, day, "Lançamento de teste");
        return financialService.create(request, Instant.now());
    }

    private EntryResponse onlyEntry(UUID orderId, EntryType type) {
        EntryResponse found = null;
        for (EntryResponse entry : financialService.findByOrder(orderId)) {
            if (entry.type() == type) {
                assertThat(found).isNull();
                found = entry;
            }
        }
        assertThat(found).isNotNull();
        return found;
    }

    private Instant noonOf(LocalDate day) {
        return day.atTime(12, 0).atZone(STORE_ZONE).toInstant();
    }

    private LocalDate today() {
        return LocalDate.now(STORE_ZONE);
    }

    private void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(expected));
    }
}
