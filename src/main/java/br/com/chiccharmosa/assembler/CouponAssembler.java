package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.CouponController;
import br.com.chiccharmosa.dto.CouponDto.CouponResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

@Component
public class CouponAssembler implements RepresentationModelAssembler<CouponResponse, EntityModel<CouponResponse>> {

    @Override
    public EntityModel<CouponResponse> toModel(CouponResponse coupon) {
        return EntityModel.of(coupon,
                linkTo(methodOn(CouponController.class).get(coupon.id())).withSelfRel(),
                linkTo(methodOn(CouponController.class).list(null)).withRel("coupons"));
    }
}
