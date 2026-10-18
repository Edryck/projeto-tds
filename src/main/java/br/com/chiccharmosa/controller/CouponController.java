package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.CouponAssembler;
import br.com.chiccharmosa.dto.CouponDto.CouponRequest;
import br.com.chiccharmosa.dto.CouponDto.CouponResponse;
import br.com.chiccharmosa.service.CouponService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;
    private final CouponAssembler couponAssembler;
    private final PagedResourcesAssembler<CouponResponse> pagedAssembler;

    public CouponController(CouponService couponService, CouponAssembler couponAssembler,
            PagedResourcesAssembler<CouponResponse> pagedAssembler) {
        this.couponService = couponService;
        this.couponAssembler = couponAssembler;
        this.pagedAssembler = pagedAssembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('coupon:edit')")
    public ResponseEntity<EntityModel<CouponResponse>> create(@Valid @RequestBody CouponRequest request) {
        boolean existed = request.id() != null && couponService.exists(request.id());
        EntityModel<CouponResponse> model = couponAssembler.toModel(couponService.create(request, Instant.now()));
        if (existed) {
            return ResponseEntity.ok(model);
        }
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(model);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('coupon:edit')")
    public PagedModel<EntityModel<CouponResponse>> list(Pageable pageable) {
        return pagedAssembler.toModel(couponService.list(pageable), couponAssembler);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('coupon:edit')")
    public EntityModel<CouponResponse> get(@PathVariable UUID id) {
        return couponAssembler.toModel(couponService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('coupon:edit')")
    public EntityModel<CouponResponse> update(@PathVariable UUID id, @Valid @RequestBody CouponRequest request) {
        return couponAssembler.toModel(couponService.update(id, request, Instant.now()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('coupon:edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        couponService.delete(id, Instant.now());
    }
}
