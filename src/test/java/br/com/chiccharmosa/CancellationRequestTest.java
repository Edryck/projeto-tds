package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.CancellationDto.CancellationView;
import br.com.chiccharmosa.dto.OrderDto.PayRequest;
import br.com.chiccharmosa.dto.SyncDto.Operation;
import br.com.chiccharmosa.dto.SyncDto.PushRequest;
import br.com.chiccharmosa.dto.SyncDto.Result;
import br.com.chiccharmosa.entity.CancellationStatus;
import br.com.chiccharmosa.entity.OrderStatus;
import br.com.chiccharmosa.entity.PaymentMethod;
import br.com.chiccharmosa.entity.SyncAction;
import br.com.chiccharmosa.entity.SyncEntity;
import br.com.chiccharmosa.entity.SyncStatus;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.service.OrderService;
import br.com.chiccharmosa.service.StockService;
import br.com.chiccharmosa.service.SyncService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

class CancellationRequestTest extends IntegrationTest {

    @Autowired
    private TestData testData;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockService stockService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    private CurrentUser admin;
    private CurrentUser cashier;
    private String cashierToken;
    private UUID variantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        cashier = testData.loginAs(testData.userWithRole(SeedIds.CASHIER_ROLE));
        cashierToken = testData.tokenFor(cashier);
        admin = testData.loginAs(testData.admin());
        variantId = testData.variantWithStock("50.00", "20.00", 5);
        orderId = orderService.create(testData.orderRequest(testData.customer(), variantId, 1), Instant.now(), false).id();
    }

    @Test
    void pendingRequestBlocksPayment() throws Exception {
        requestFor(orderId);
        assertThatThrownBy(() -> orderService.pay(orderId, PaymentMethod.PIX, testData.uniqueInstant()))
                .hasMessage("Pedido com cancelamento pendente");
    }

    @Test
    void secondPendingRequestIsRejected() throws Exception {
        requestFor(orderId);
        assertThatThrownBy(() -> requestFor(orderId)).hasMessage("Já existe uma solicitação pendente para este pedido");
    }

    @Test
    void approvalCancelsOrderAndReturnsStock() throws Exception {
        int before = stockService.balance(variantId);
        CancellationView request = requestFor(orderId);
        CancellationView approved = orderService.approveCancellation(request.id(), null, testData.uniqueInstant());
        assertThat(orderService.get(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockService.balance(variantId)).isEqualTo(before + 1);
        assertThat(approved.status()).isEqualTo(CancellationStatus.APPROVED);
        assertThatThrownBy(() -> orderService.approveCancellation(request.id(), null, testData.uniqueInstant()))
                .hasMessage("Solicitação já decidida");
    }

    @Test
    void rejectionReleasesOrder() throws Exception {
        CancellationView request = requestFor(orderId);
        orderService.rejectCancellation(request.id(), "cliente confirmou", testData.uniqueInstant());
        assertThat(orderService.pay(orderId, PaymentMethod.PIX, testData.uniqueInstant()).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void directCancelApprovesPendingRequest() throws Exception {
        CancellationView request = requestFor(orderId);
        orderService.cancel(orderId, testData.uniqueInstant());
        List<CancellationView> approved = orderService.listCancellations(CancellationStatus.APPROVED, PageRequest.of(0, 50)).getContent();
        assertThat(approved).extracting(CancellationView::id).contains(request.id());
        CancellationView decided = approved.stream().filter(view -> view.id().equals(request.id())).findFirst().orElseThrow();
        assertThat(decided.decidedByName()).isEqualTo(testData.admin().getName());
    }

    @Test
    void cancelledOrderCannotBeRequested() {
        orderService.cancel(orderId, testData.uniqueInstant());
        assertThatThrownBy(() -> requestFor(orderId)).hasMessage("Pedido não pode ser cancelado");
    }

    @Test
    void syncPayWithPendingRequestIsRejected() throws Exception {
        requestFor(orderId);
        Operation operation = new Operation(UUID.randomUUID(), SyncEntity.ORDER, SyncAction.PAY, orderId,
                objectMapper.valueToTree(new PayRequest(PaymentMethod.PIX)), Instant.now());
        Result result = syncService.push(new PushRequest(List.of(operation)), admin).results().get(0);
        assertThat(result.status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(result.reason()).isEqualTo("Pedido com cancelamento pendente");
    }

    @Test
    void cashierGets403OnApprove() throws Exception {
        CancellationView request = requestFor(orderId);
        mockMvc.perform(post("/api/cancellation-requests/" + request.id() + "/approve").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void orderWithPendingRequestHidesPayLink() throws Exception {
        requestFor(orderId);
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCancellation.reason").value("cliente desistiu"))
                .andExpect(jsonPath("$._links.pay").doesNotExist())
                .andExpect(jsonPath("$._links.request-cancel").doesNotExist());
    }

    private CancellationView requestFor(UUID id) throws Exception {
        try {
            return testData.runAs(cashier, () -> orderService.requestCancellation(id, "cliente desistiu", testData.uniqueInstant()));
        } finally {
            testData.actAs(admin);
        }
    }
}
