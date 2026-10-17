package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chiccharmosa.dto.StockDto.EntryItem;
import br.com.chiccharmosa.dto.StockDto.StockEntryRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
import br.com.chiccharmosa.entity.MovementType;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.StockMovementRepository;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.service.CatalogService;
import br.com.chiccharmosa.service.StockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class StockServiceTest extends IntegrationTest {

    @Autowired
    private TestData testData;

    @Autowired
    private StockService stockService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private StockMovementRepository movementRepository;

    private CurrentUser admin;

    @BeforeEach
    void setUp() {
        admin = testData.loginAs(testData.admin());
    }

    @Test
    void balanceIsSumOfMovements() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 10);

        stockService.adjust(variantId, -3, "Peça com defeito", Instant.now());
        stockService.withdraw(variantId, 2, UUID.randomUUID(), false);

        assertThat(stockService.balance(variantId)).isEqualTo(5);
    }

    @Test
    void withdrawBelowBalanceFailsWith409() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 1);

        assertThatThrownBy(() -> stockService.withdraw(variantId, 2, UUID.randomUUID(), false))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(stockService.balance(variantId)).isEqualTo(1);
    }

    @Test
    void withdrawAllowNegativeGoesBelowZero() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 1);

        stockService.withdraw(variantId, 3, UUID.randomUUID(), true);

        assertThat(stockService.balance(variantId)).isEqualTo(-2);
    }

    @Test
    void negativeAdjustmentAboveBalanceFailsWith409() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 2);

        assertThatThrownBy(() -> stockService.adjust(variantId, -3, "Contagem", Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(stockService.balance(variantId)).isEqualTo(2);
    }

    @Test
    void twoSimultaneousWithdrawalsOfLastUnit() throws Exception {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<HttpStatus>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return testData.runAs(admin, () -> withdrawOne(variantId));
            }));
        }
        start.countDown();

        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<HttpStatus> future : futures) {
            statuses.add(future.get());
        }
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(stockService.balance(variantId)).isZero();
    }

    @Test
    void reverseIsIdempotent() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        UUID orderId = UUID.randomUUID();
        stockService.withdraw(variantId, 2, orderId, false);

        stockService.reverse(orderId);
        stockService.reverse(orderId);

        assertThat(stockService.balance(variantId)).isEqualTo(5);
        assertThat(movementRepository.countByReferenceIdAndType(orderId, MovementType.REVERSAL)).isEqualTo(1);
    }

    @Test
    void soldOutWhenBalanceZeroOrBelow() {
        VariantResponse created = testData.variant("50.00", "20.00");
        assertThat(catalogService.findVariant(created.id()).status()).isEqualTo("SOLD_OUT");

        testData.stockIn(created.id(), 1);
        assertThat(catalogService.findVariant(created.id()).status()).isEqualTo("AVAILABLE");

        stockService.withdraw(created.id(), 3, UUID.randomUUID(), true);
        VariantResponse negative = catalogService.findVariant(created.id());
        assertThat(negative.balance()).isEqualTo(-2);
        assertThat(negative.status()).isEqualTo("SOLD_OUT");
    }

    @Test
    void entryReplayDoesNotAddStockTwice() {
        UUID variantId = testData.variant("50.00", "20.00").id();
        StockEntryRequest entry = testData.entryRequest(variantId, 4);

        stockService.registerEntry(entry, Instant.now());
        stockService.registerEntry(entry, Instant.now());

        assertThat(stockService.balance(variantId)).isEqualTo(4);
    }

    @Test
    void entryWithUnknownVariantSavesNothing() {
        UUID variantId = testData.variant("50.00", "20.00").id();
        List<EntryItem> items = List.of(
                new EntryItem(UUID.randomUUID(), variantId, 3, new BigDecimal("10.00")),
                new EntryItem(UUID.randomUUID(), UUID.randomUUID(), 3, new BigDecimal("10.00")));
        StockEntryRequest entry = new StockEntryRequest(UUID.randomUUID(), null, LocalDate.now(), null, items);

        assertThatThrownBy(() -> stockService.registerEntry(entry, Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(stockService.balance(variantId)).isZero();
    }

    private HttpStatus withdrawOne(UUID variantId) {
        try {
            stockService.withdraw(variantId, 1, UUID.randomUUID(), false);
            return HttpStatus.OK;
        } catch (BusinessException error) {
            return error.getStatus();
        }
    }
}
