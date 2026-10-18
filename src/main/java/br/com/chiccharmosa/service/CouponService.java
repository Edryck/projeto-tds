package br.com.chiccharmosa.service;

import br.com.chiccharmosa.dto.CouponDto.CouponRequest;
import br.com.chiccharmosa.dto.CouponDto.CouponResponse;
import br.com.chiccharmosa.entity.Coupon;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.repository.CouponRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CouponService {

    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public boolean exists(UUID id) {
        return couponRepository.existsById(id);
    }

    @Transactional
    public CouponResponse create(CouponRequest request, Instant changedAt) {
        if (request.id() != null) {
            Coupon existing = couponRepository.findById(request.id()).orElse(null);
            if (existing != null) {
                return CouponResponse.from(existing);
            }
        }
        Coupon coupon = new Coupon();
        coupon.setId(request.id() == null ? UUID.randomUUID() : request.id());
        coupon.setUsedCount(0);
        applyRequest(coupon, request);
        coupon.touch(changedAt);
        return CouponResponse.from(couponRepository.save(coupon));
    }

    public CouponResponse get(UUID id) {
        return CouponResponse.from(findActiveCoupon(id));
    }

    public Page<CouponResponse> list(Pageable pageable) {
        return couponRepository.findByDeletedAtIsNull(pageable).map(CouponResponse::from);
    }

    @Transactional
    public CouponResponse update(UUID id, CouponRequest request, Instant changedAt) {
        Coupon coupon = findActiveCoupon(id);
        applyRequest(coupon, request);
        coupon.touch(changedAt);
        return CouponResponse.from(coupon);
    }

    @Transactional
    public void delete(UUID id, Instant changedAt) {
        findActiveCoupon(id).markDeleted(changedAt);
    }

    // trava a linha do cupom para dois pedidos simultâneos não passarem do limite de uso
    @Transactional
    public Coupon redeem(String code, Instant now) {
        Coupon coupon = couponRepository.findLockedByCode(code.trim())
                .orElseThrow(() -> new BusinessException(UNPROCESSABLE, "Cupom inválido"));
        boolean outOfValidity = now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil());
        if (outOfValidity || coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new BusinessException(UNPROCESSABLE, "Cupom inválido");
        }
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        coupon.touch(now);
        return coupon;
    }

    private Coupon findActiveCoupon(UUID id) {
        return couponRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Cupom não encontrado"));
    }

    private void applyRequest(Coupon coupon, CouponRequest request) {
        if (request.validUntil().isBefore(request.validFrom())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Validade inválida");
        }
        coupon.setCode(request.code().trim());
        coupon.setDiscountPercent(request.discountPercent());
        coupon.setValidFrom(request.validFrom());
        coupon.setValidUntil(request.validUntil());
        coupon.setUsageLimit(request.usageLimit());
    }
}
