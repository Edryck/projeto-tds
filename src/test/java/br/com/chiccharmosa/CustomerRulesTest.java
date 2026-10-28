package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.CustomerDto.CustomerRequest;
import br.com.chiccharmosa.dto.CustomerDto.CustomerView;
import br.com.chiccharmosa.dto.OrderDto.OrderResponse;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.service.CustomerService;
import br.com.chiccharmosa.service.OrderService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class CustomerRulesTest extends IntegrationTest {

    @Autowired
    private TestData testData;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        testData.loginAs(testData.admin());
    }

    @Test
    void defaultCustomerCannotBeUpdated() {
        CustomerRequest request = new CustomerRequest(SeedIds.DEFAULT_CUSTOMER, "Outro nome", null, null, null);
        assertThatThrownBy(() -> customerService.update(SeedIds.DEFAULT_CUSTOMER, request, testData.uniqueInstant()))
                .isInstanceOf(BusinessException.class).hasMessage("Cliente padrão não pode ser alterada");
    }

    @Test
    void defaultCustomerCannotBeDeleted() {
        assertThatThrownBy(() -> customerService.delete(SeedIds.DEFAULT_CUSTOMER, testData.uniqueInstant()))
                .isInstanceOf(BusinessException.class).hasMessage("Cliente padrão não pode ser alterada");
    }

    @Test
    void defaultCustomerIsHiddenFromList() {
        Page<CustomerView> byQuery = customerService.listViews("Consumidor", null, null, PageRequest.of(0, 20));
        Page<CustomerView> byName = customerService.listViews(null, "Consumidor", null, PageRequest.of(0, 20));
        Page<CustomerView> all = customerService.listViews(null, null, null, PageRequest.of(0, 200));
        assertThat(byQuery.getContent()).isEmpty();
        assertThat(byName.getContent()).isEmpty();
        assertThat(all.getContent()).noneMatch(view -> view.id().equals(SeedIds.DEFAULT_CUSTOMER));
    }

    @Test
    void searchMatchesEmailIgnoringCase() {
        UUID id = UUID.randomUUID();
        customerService.create(new CustomerRequest(id, "Ana Busca", null, "ana.busca@example.com", null), Instant.now());
        Page<CustomerView> page = customerService.listViews("ANA.BUSCA@", null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(CustomerView::id).containsExactly(id);
    }

    @Test
    void lastOrderAtIgnoresCancelledOrders() {
        UUID customerId = testData.customer();
        UUID variantId = testData.variantWithStock("50.00", "20.00", 5);
        OrderResponse order = orderService.create(testData.orderRequest(customerId, variantId, 1), testData.uniqueInstant(), false);
        assertThat(customerService.getView(customerId).lastOrderAt()).isNotNull();
        orderService.cancel(order.id(), testData.uniqueInstant());
        assertThat(customerService.getView(customerId).lastOrderAt()).isNull();
    }
}
