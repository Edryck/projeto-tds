package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chiccharmosa.dto.OrderDto.ItemRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.dto.VariantDto.VariantRequest;
import br.com.chiccharmosa.dto.VariantDto.VariantResponse;
import br.com.chiccharmosa.entity.MovementType;
import br.com.chiccharmosa.entity.OrderStatus;
import br.com.chiccharmosa.entity.PaymentMethod;
import br.com.chiccharmosa.entity.ShippingMethod;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.OrderRepository;
import br.com.chiccharmosa.repository.StockMovementRepository;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.service.CatalogService;
import br.com.chiccharmosa.service.OrderService;
import br.com.chiccharmosa.service.StockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class OrderStateTest extends IntegrationTest {

    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

    @Autowired
    private TestData testData;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockService stockService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private MockMvc mockMvc;

    private CurrentUser admin;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        admin = testData.loginAs(testData.admin());
        customerId = testData.customer();
    }

    @Test
    void createReducesStock() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);

        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 2), Instant.now(), false);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockService.balance(variantId)).isEqualTo(3);
    }

    @Test
    void createCopiesUnitPriceAndCost() {
        VariantResponse variant = testData.variant("50.00", "20.00");
        testData.stockIn(variant.id(), 5);
        OrderResponse order = orderService.create(testData.orderRequest(customerId, variant.id(), 1), Instant.now(), false);

        VariantRequest changed = new VariantRequest(variant.id(), "Preto", "M", null, new BigDecimal("80.00"), new BigDecimal("35.00"));
        catalogService.updateVariant(variant.id(), changed, Instant.now());

        OrderResponse reloaded = orderService.get(order.id());
        assertThat(reloaded.items().get(0).unitPrice()).isEqualByComparingTo("50.00");
        assertThat(reloaded.items().get(0).unitCost()).isEqualByComparingTo("20.00");
    }

    @Test
    void lastUnitSoldTwiceFailsSecondWith409() throws Exception {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<HttpStatus>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            OrderRequest request = testData.orderRequest(customerId, variantId, 1);
            futures.add(executor.submit(() -> {
                start.await();
                return testData.runAs(admin, () -> createStatus(request));
            }));
        }
        start.countDown();

        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<HttpStatus> future : futures) {
            statuses.add(future.get());
        }
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(stockService.balance(variantId)).isZero();
    }

    @Test
    void failedSecondItemRollsBackFirst() {
        UUID withStock = testData.variantWithStock("50.00", "20.00", 5);
        UUID withoutStock = testData.variant("30.00", "10.00").id();
        List<ItemRequest> items = List.of(
                new ItemRequest(UUID.randomUUID(), withStock, 2),
                new ItemRequest(UUID.randomUUID(), withoutStock, 1));
        OrderRequest request = new OrderRequest(UUID.randomUUID(), customerId, null, null, null, items);

        assertStatus(() -> orderService.create(request, Instant.now(), false), HttpStatus.CONFLICT);

        assertThat(stockService.balance(withStock)).isEqualTo(5);
        assertThat(orderRepository.existsById(request.id())).isFalse();
    }

    @Test
    void createWithSameIdTwiceDoesNotDuplicateStockMovement() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderRequest request = testData.orderRequest(customerId, variantId, 2);

        orderService.create(request, Instant.now(), false);
        orderService.create(request, Instant.now(), false);

        assertThat(stockService.balance(variantId)).isEqualTo(3);
        assertThat(movementRepository.countByReferenceIdAndType(request.id(), MovementType.SALE)).isEqualTo(1);
    }

    @Test
    void createWithAllowNegativeKeepsOrderAndGoesBelowZero() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 1);

        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 3), Instant.now(), true);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockService.balance(variantId)).isEqualTo(-2);
    }

    @Test
    void repeatedVariantItemsAreSummed() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 1);
        List<ItemRequest> items = List.of(
                new ItemRequest(UUID.randomUUID(), variantId, 1),
                new ItemRequest(UUID.randomUUID(), variantId, 1));
        OrderRequest request = new OrderRequest(UUID.randomUUID(), customerId, null, null, null, items);

        assertStatus(() -> orderService.create(request, Instant.now(), false), HttpStatus.CONFLICT);
        assertThat(stockService.balance(variantId)).isEqualTo(1);
    }

    @Test
    void shippingCostAddsToTotal() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);

        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 2, "15.00", null), Instant.now(), false);

        assertThat(order.subtotal()).isEqualByComparingTo("100.00");
        assertThat(order.discount()).isEqualByComparingTo("0.00");
        assertThat(order.shippingCost()).isEqualByComparingTo("15.00");
        assertThat(order.total()).isEqualByComparingTo("115.00");
    }

    @Test
    void missingShippingCostIsZero() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);

        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 1), Instant.now(), false);

        assertThat(order.shippingCost()).isEqualByComparingTo("0.00");
        assertThat(order.total()).isEqualByComparingTo("50.00");
    }

    @Test
    void negativeShippingCostOnCreateIsRejectedWith400() throws Exception {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        String body = """
                {"id":"%s","customerId":"%s","shippingCost":-1,"items":[{"id":"%s","variantId":"%s","quantity":1}]}
                """.formatted(UUID.randomUUID(), customerId, UUID.randomUUID(), variantId);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + testData.tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(stockService.balance(variantId)).isEqualTo(5);
    }

    private HttpStatus createStatus(OrderRequest request) {
        try {
            orderService.create(request, Instant.now(), false);
            return HttpStatus.CREATED;
        } catch (BusinessException error) {
            return error.getStatus();
        }
    }

    private void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(expected));
    }

    @Test
    void validTransitionsAreAccepted() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = pendingOrder(variantId, 1);

        assertThat(orderService.pay(order.id(), PaymentMethod.PIX, Instant.now()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(orderService.ship(order.id(), ShippingMethod.POST_OFFICE, "BR123456789BR", Instant.now()).status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(orderService.deliver(order.id(), Instant.now()).status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void invalidTransitionsAreRejected() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 10);

        OrderResponse pending = pendingOrder(variantId, 1);
        assertStatus(() -> orderService.ship(pending.id(), ShippingMethod.IN_PERSON, null, Instant.now()), UNPROCESSABLE);
        assertStatus(() -> orderService.deliver(pending.id(), Instant.now()), UNPROCESSABLE);

        OrderResponse delivered = pendingOrder(variantId, 1);
        orderService.pay(delivered.id(), PaymentMethod.PIX, Instant.now());
        orderService.ship(delivered.id(), ShippingMethod.IN_PERSON, null, Instant.now());
        orderService.deliver(delivered.id(), Instant.now());
        assertStatus(() -> orderService.pay(delivered.id(), PaymentMethod.PIX, Instant.now()), UNPROCESSABLE);
        assertStatus(() -> orderService.cancel(delivered.id(), Instant.now()), UNPROCESSABLE);

        OrderResponse shipped = pendingOrder(variantId, 1);
        orderService.pay(shipped.id(), PaymentMethod.CARD, Instant.now());
        orderService.ship(shipped.id(), ShippingMethod.IN_PERSON, null, Instant.now());
        assertStatus(() -> orderService.cancel(shipped.id(), Instant.now()), UNPROCESSABLE);

        OrderResponse cancelled = pendingOrder(variantId, 1);
        orderService.cancel(cancelled.id(), Instant.now());
        assertStatus(() -> orderService.pay(cancelled.id(), PaymentMethod.CASH, Instant.now()), UNPROCESSABLE);
    }

    @Test
    void cancelFromPendingAndPaidReturnsStockOnce() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);

        OrderResponse pending = pendingOrder(variantId, 2);
        orderService.cancel(pending.id(), Instant.now());
        assertStatus(() -> orderService.cancel(pending.id(), Instant.now()), UNPROCESSABLE);
        assertThat(stockService.balance(variantId)).isEqualTo(5);

        OrderResponse paid = pendingOrder(variantId, 3);
        orderService.pay(paid.id(), PaymentMethod.PIX, Instant.now());
        assertThat(orderService.cancel(paid.id(), Instant.now()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockService.balance(variantId)).isEqualTo(5);
    }

    @Test
    void postOfficeShipmentRequiresTrackingCode() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = pendingOrder(variantId, 1);
        orderService.pay(order.id(), PaymentMethod.PIX, Instant.now());

        assertStatus(() -> orderService.ship(order.id(), ShippingMethod.POST_OFFICE, null, Instant.now()), UNPROCESSABLE);
        assertStatus(() -> orderService.ship(order.id(), ShippingMethod.POST_OFFICE, " ", Instant.now()), UNPROCESSABLE);
        assertThat(orderService.ship(order.id(), ShippingMethod.IN_PERSON, null, Instant.now()).status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shippingCostCanChangeOnlyWhilePending() {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 2, "10.00", null), Instant.now(), false);

        OrderResponse changed = orderService.updateShippingCost(order.id(), new BigDecimal("20.00"), Instant.now());
        assertThat(changed.shippingCost()).isEqualByComparingTo("20.00");
        assertThat(changed.total()).isEqualByComparingTo("120.00");

        orderService.pay(order.id(), PaymentMethod.PIX, Instant.now());
        assertStatus(() -> orderService.updateShippingCost(order.id(), new BigDecimal("5.00"), Instant.now()), UNPROCESSABLE);
        assertThat(orderService.get(order.id()).total()).isEqualByComparingTo("120.00");
    }

    @Test
    void negativeShippingCostOnPatchIsRejectedWith400() throws Exception {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = pendingOrder(variantId, 1);

        mockMvc.perform(patch("/api/orders/{id}/shipping-cost", order.id())
                        .header("Authorization", "Bearer " + testData.tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingCost\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assemblerLinksFollowStatus() throws Exception {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = pendingOrder(variantId, 1);
        String token = testData.tokenFor(admin);

        mockMvc.perform(get("/api/orders/{id}", order.id())
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.pay.href").exists())
                .andExpect(jsonPath("$._links.cancel.href").exists())
                .andExpect(jsonPath("$._links['shipping-cost'].href").exists())
                .andExpect(jsonPath("$._links.ship").doesNotExist());

        testData.actAs(admin);
        orderService.pay(order.id(), PaymentMethod.PIX, Instant.now());
        orderService.ship(order.id(), ShippingMethod.IN_PERSON, null, Instant.now());
        orderService.deliver(order.id(), Instant.now());

        mockMvc.perform(get("/api/orders/{id}", order.id())
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.pay").doesNotExist())
                .andExpect(jsonPath("$._links.ship").doesNotExist())
                .andExpect(jsonPath("$._links.deliver").doesNotExist())
                .andExpect(jsonPath("$._links.cancel").doesNotExist());
    }

    @Test
    void actionLinksRespectPermissions() throws Exception {
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = pendingOrder(variantId, 1);
        CurrentUser viewer = testData.loginAs(testData.userWith("order:view"));

        mockMvc.perform(get("/api/orders/{id}", order.id())
                        .header("Authorization", "Bearer " + testData.tokenFor(viewer))
                        .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.pay").doesNotExist())
                .andExpect(jsonPath("$._links.cancel").doesNotExist());
    }

    private OrderResponse pendingOrder(UUID variantId, int quantity) {
        return orderService.create(testData.orderRequest(customerId, variantId, quantity), Instant.now(), false);
    }
}
