package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chiccharmosa.config.SeedIds;
import br.com.chiccharmosa.dto.CustomerDto.CustomerRequest;
import br.com.chiccharmosa.dto.FinanceDto.EntryRequest;
import br.com.chiccharmosa.dto.FinanceDto.EntryResponse;
import br.com.chiccharmosa.dto.SyncDto.Item;
import br.com.chiccharmosa.dto.SyncDto.PullResponse;
import br.com.chiccharmosa.entity.EntryType;
import br.com.chiccharmosa.entity.SyncEntity;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.service.CustomerService;
import br.com.chiccharmosa.service.FinancialService;
import br.com.chiccharmosa.service.SyncService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SyncPullTest extends IntegrationTest {

    @Autowired
    private TestData testData;

    @Autowired
    private SyncService syncService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private FinancialService financialService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CurrentUser admin;

    @BeforeEach
    void setUp() {
        admin = testData.loginAs(testData.admin());
    }

    @Test
    void pullReturnsOnlyChangesAfterSince() {
        Instant since = testData.uniqueInstant();
        UUID old = customerAt(since.minus(Duration.ofHours(1)));
        UUID recent = customerAt(since.plusSeconds(1));

        List<UUID> ids = idsOf(syncService.pull(since, null, 500, admin));

        assertThat(ids).contains(recent).doesNotContain(old);
    }

    @Test
    void pullIncludesSoftDeletedRecordsWithDeletedFlag() {
        Instant since = testData.uniqueInstant();
        UUID id = customerService.create(new CustomerRequest(UUID.randomUUID(), "Excluída", null, null, null), Instant.now()).id();
        customerService.delete(id, Instant.now());
        backdate("customer", id, since.plusSeconds(1));

        Item item = itemWithId(syncService.pull(since, null, 500, admin), id);

        assertThat(item.entity()).isEqualTo(SyncEntity.CUSTOMER);
        assertThat(item.deleted()).isTrue();
        assertThat(item.data()).isNull();
    }

    @Test
    void pullOmitsEntitiesUserCannotView() {
        Instant since = testData.uniqueInstant();
        EntryRequest request = new EntryRequest(UUID.randomUUID(), SeedIds.STORE_ACCOUNT, SeedIds.OTHER_CATEGORY,
                EntryType.EXPENSE, new BigDecimal("10.00"), null, testData.uniqueDay(), "Despesa");
        EntryResponse entry = financialService.create(request, Instant.now());
        backdate("financial_entry", entry.id(), since.plusSeconds(1));
        UUID customerId = customerAt(since.plusSeconds(2));
        CurrentUser reader = testData.loginAs(testData.userWith("sync:use", "customer:view"));

        PullResponse response = syncService.pull(since, null, 500, reader);

        assertThat(idsOf(response)).contains(customerId).doesNotContain(entry.id());
        for (Item item : response.items()) {
            assertThat(item.entity()).isNotEqualTo(SyncEntity.FINANCIAL_ENTRY);
        }
    }

    @Test
    void pullRereadsFiveSecondOverlap() {
        Instant since = testData.uniqueInstant();
        UUID inside = customerAt(since.minusSeconds(3));
        UUID outside = customerAt(since.minusSeconds(10));

        List<UUID> ids = idsOf(syncService.pull(since, null, 500, admin));

        assertThat(ids).contains(inside).doesNotContain(outside);
    }

    @Test
    void pagesAdvanceWithoutOverlapInsideSession() {
        Instant since = testData.uniqueInstant();
        UUID first = customerAt(since.plusSeconds(1));
        UUID second = customerAt(since.plusSeconds(2));
        UUID third = customerAt(since.plusSeconds(3));

        PullResponse page = syncService.pull(since, null, 2, admin);
        PullResponse next = syncService.pull(page.nextSince(), page.nextAfterId(), 2, admin);

        assertThat(idsOf(page)).containsExactly(first, second);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextSince()).isEqualTo(since.plusSeconds(2));
        assertThat(page.nextAfterId()).isEqualTo(second);
        assertThat(idsOf(next).get(0)).isEqualTo(third);
        assertThat(idsOf(next)).doesNotContain(first, second);
    }

    @Test
    void burstInsideOverlapWindowStillFinishes() {
        Instant since = testData.uniqueInstant();
        List<UUID> burst = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            burst.add(customerAt(since.plusMillis(100)));
        }

        PullResponse page = syncService.pull(since, null, 2, admin);
        List<UUID> seen = new ArrayList<>(idsOf(page));
        for (int i = 0; i < 3; i++) {
            page = syncService.pull(page.nextSince(), page.nextAfterId(), 2, admin);
            seen.addAll(idsOf(page));
        }

        assertThat(seen).containsAll(burst);
    }

    private UUID customerAt(Instant updatedAt) {
        UUID id = customerService.create(new CustomerRequest(UUID.randomUUID(), "Cliente", null, null, null), Instant.now()).id();
        backdate("customer", id, updatedAt);
        return id;
    }

    private void backdate(String table, UUID id, Instant updatedAt) {
        jdbcTemplate.update("update " + table + " set updated_at = ? where id = ?", updatedAt.atOffset(ZoneOffset.UTC), id);
    }

    private List<UUID> idsOf(PullResponse response) {
        List<UUID> ids = new ArrayList<>();
        for (Item item : response.items()) {
            ids.add(item.id());
        }
        return ids;
    }

    private Item itemWithId(PullResponse response, UUID id) {
        for (Item item : response.items()) {
            if (item.id().equals(id)) {
                return item;
            }
        }
        throw new AssertionError("Item não veio no pull: " + id);
    }
}
