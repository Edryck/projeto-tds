package br.com.chiccharmosa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.chiccharmosa.config.SeedIds;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywaySchemaTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void adminHasAllPermissions() {
        Integer adminPermissions = jdbc.queryForObject(
                "select count(*) from role_permission where role_id = ?", Integer.class, SeedIds.ADMIN_ROLE);
        Integer allPermissions = jdbc.queryForObject("select count(*) from permission", Integer.class);

        assertThat(allPermissions).isPositive();
        assertThat(adminPermissions).isEqualTo(allPermissions);
    }

    @Test
    void stockMovementRejectsZeroQuantity() {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        jdbc.update("insert into category (id, name, created_at, updated_at, changed_at) "
                + "values (?, 'Test', now(), now(), now())", categoryId);
        jdbc.update("insert into product (id, category_id, name, created_at, updated_at, changed_at) "
                + "values (?, ?, 'Test', now(), now(), now())", productId, categoryId);
        jdbc.update("insert into product_variant (id, product_id, color, size, price, cost, created_at, updated_at, changed_at) "
                + "values (?, ?, 'Black', 'M', 10.00, 5.00, now(), now(), now())", variantId, productId);

        assertThatThrownBy(() -> jdbc.update(
                "insert into stock_movement (id, variant_id, quantity, type, occurred_at, created_at, updated_at, changed_at) "
                        + "values (?, ?, 0, 'ADJUSTMENT', now(), now(), now(), now())",
                UUID.randomUUID(), variantId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_movement_quantity");
    }

    @Test
    void cashClosingIsUniquePerAccountPerDay() {
        LocalDate day = LocalDate.of(1999, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000));
        String sql = "insert into cash_closing (id, account_id, closing_date, pix_total, card_total, cash_total, "
                + "expense_total, created_at, updated_at, changed_at) values (?, ?, ?, 0, 0, 0, 0, now(), now(), now())";

        jdbc.update(sql, UUID.randomUUID(), SeedIds.STORE_ACCOUNT, day);

        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), SeedIds.STORE_ACCOUNT, day))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
