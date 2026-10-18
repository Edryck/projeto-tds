package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.Coupon;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class CouponDto {

    private CouponDto() {
    }

    public record CouponRequest(UUID id, @NotBlank @Size(max = 50) String code,
            @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal discountPercent,
            @NotNull Instant validFrom, @NotNull Instant validUntil, @Positive int usageLimit) {
    }

    @Relation(collectionRelation = "coupons")
    public record CouponResponse(UUID id, String code, BigDecimal discountPercent, Instant validFrom,
            Instant validUntil, int usageLimit, int usedCount) {

        public static CouponResponse from(Coupon coupon) {
            return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getDiscountPercent(),
                    coupon.getValidFrom(), coupon.getValidUntil(), coupon.getUsageLimit(), coupon.getUsedCount());
        }
    }
}
