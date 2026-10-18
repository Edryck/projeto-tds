package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chiccharmosa.dto.CouponDto.CouponRequest;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.service.CouponService;
import br.com.chiccharmosa.service.OrderService;
import br.com.chiccharmosa.service.StockService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class CouponTest extends IntegrationTest {

    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

    @Autowired
    private TestData testData;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private StockService stockService;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        testData.loginAs(testData.admin());
        customerId = testData.customer();
    }

    @Test
    void couponDiscountReducesTotal() {
        UUID variantId = testData.variantWithStock("100.00", "40.00", 5);
        String code = coupon("10", Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(1)), 5);

        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 1, null, code), Instant.now(), false);

        assertThat(order.discount()).isEqualByComparingTo("10.00");
        assertThat(order.total()).isEqualByComparingTo("90.00");
    }

    @Test
    void couponDoesNotApplyToShipping() {
        UUID variantId = testData.variantWithStock("100.00", "40.00", 5);
        String code = coupon("10", Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(1)), 5);

        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 1, "20.00", code), Instant.now(), false);

        assertThat(order.subtotal()).isEqualByComparingTo("100.00");
        assertThat(order.discount()).isEqualByComparingTo("10.00");
        assertThat(order.shippingCost()).isEqualByComparingTo("20.00");
        assertThat(order.total()).isEqualByComparingTo("110.00");
    }

    @Test
    void couponBeyondUsageLimitIsRejected() {
        UUID variantId = testData.variantWithStock("100.00", "40.00", 5);
        String code = coupon("10", Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(1)), 1);
        orderService.create(testData.orderRequest(customerId, variantId, 1, null, code), Instant.now(), false);

        assertThatThrownBy(() -> orderService.create(testData.orderRequest(customerId, variantId, 1, null, code), Instant.now(), false))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(UNPROCESSABLE));
    }

    @Test
    void expiredCouponIsRejectedAndStockIsUntouched() {
        UUID variantId = testData.variantWithStock("100.00", "40.00", 5);
        String code = coupon("10", Instant.now().minus(Duration.ofDays(10)), Instant.now().minus(Duration.ofHours(1)), 5);

        assertThatThrownBy(() -> orderService.create(testData.orderRequest(customerId, variantId, 1, null, code), Instant.now(), false))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getStatus()).isEqualTo(UNPROCESSABLE));
        assertThat(stockService.balance(variantId)).isEqualTo(5);
    }

    private String coupon(String percent, Instant validFrom, Instant validUntil, int usageLimit) {
        String code = "CUPOM-" + UUID.randomUUID();
        CouponRequest request = new CouponRequest(UUID.randomUUID(), code, new BigDecimal(percent), validFrom, validUntil, usageLimit);
        couponService.create(request, Instant.now());
        return code;
    }
}
