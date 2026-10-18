package br.com.chiccharmosa.assembler;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.controller.FinanceController;
import br.com.chiccharmosa.dto.FinanceDto.AccountResponse;
import br.com.chiccharmosa.dto.FinanceDto.EntryResponse;
import br.com.chiccharmosa.dto.FinanceDto.FinancialCategoryResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class FinanceAssembler implements RepresentationModelAssembler<EntryResponse, EntityModel<EntryResponse>> {

    @Override
    public EntityModel<EntryResponse> toModel(EntryResponse entry) {
        EntityModel<EntryResponse> model = EntityModel.of(entry,
                linkTo(methodOn(FinanceController.class).getEntry(entry.id())).withSelfRel(),
                linkTo(methodOn(FinanceController.class).listEntries(null, null, null, null, null))
                        .withRel("entries"),
                Link.of(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/financial-entries")
                        .queryParam("accountId", entry.accountId())
                        .toUriString(), "account-entries"));
        if (entry.orderId() != null) {
            model.add(Link.of(ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/orders/{id}")
                    .buildAndExpand(entry.orderId())
                    .toUriString(), "order"));
        }
        return model;
    }

    public EntityModel<FinancialCategoryResponse> toCategoryModel(FinancialCategoryResponse category) {
        return EntityModel.of(category,
                linkTo(methodOn(FinanceController.class).listCategories(null)).withRel("categories"));
    }

    public EntityModel<AccountResponse> toAccountModel(AccountResponse account) {
        return EntityModel.of(account,
                linkTo(methodOn(FinanceController.class).listAccounts()).withRel("accounts"));
    }
}
