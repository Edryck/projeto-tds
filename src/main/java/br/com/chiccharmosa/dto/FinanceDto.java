package br.com.chiccharmosa.dto;

import br.com.chiccharmosa.entity.AccountType;
import br.com.chiccharmosa.entity.CashClosing;
import br.com.chiccharmosa.entity.EntryType;
import br.com.chiccharmosa.entity.FinancialAccount;
import br.com.chiccharmosa.entity.FinancialCategory;
import br.com.chiccharmosa.entity.FinancialEntry;
import br.com.chiccharmosa.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.hateoas.server.core.Relation;

public final class FinanceDto {

    private FinanceDto() {
    }

    @Relation(collectionRelation = "accounts")
    public record AccountResponse(UUID id, String name, AccountType type) {

        public static AccountResponse from(FinancialAccount account) {
            return new AccountResponse(account.getId(), account.getName(), account.getType());
        }
    }

    public record FinancialCategoryRequest(UUID id, @NotBlank @Size(max = 100) String name,
            @NotNull EntryType type) {
    }

    @Relation(collectionRelation = "categories")
    public record FinancialCategoryResponse(UUID id, String name, EntryType type) {

        public static FinancialCategoryResponse from(FinancialCategory category) {
            return new FinancialCategoryResponse(category.getId(), category.getName(), category.getType());
        }
    }

    public record EntryRequest(UUID id, @NotNull UUID accountId, @NotNull UUID categoryId, @NotNull EntryType type,
            @NotNull @Positive BigDecimal amount, PaymentMethod paymentMethod, @NotNull LocalDate entryDate,
            @Size(max = 500) String description) {
    }

    @Relation(collectionRelation = "entries")
    public record EntryResponse(UUID id, UUID accountId, UUID categoryId, EntryType type, BigDecimal amount,
            PaymentMethod paymentMethod, LocalDate entryDate, String description, UUID orderId) {

        public static EntryResponse from(FinancialEntry entry) {
            return new EntryResponse(entry.getId(), entry.getAccountId(), entry.getCategoryId(), entry.getType(),
                    entry.getAmount(), entry.getPaymentMethod(), entry.getEntryDate(), entry.getDescription(),
                    entry.getOrderId());
        }
    }

    public record CashClosingRequest(UUID id, @NotNull UUID accountId, @NotNull LocalDate closingDate) {
    }

    @Relation(collectionRelation = "cashClosings")
    public record CashClosingResponse(UUID id, UUID accountId, LocalDate closingDate, BigDecimal pixTotal,
            BigDecimal cardTotal, BigDecimal cashTotal, BigDecimal expenseTotal, BigDecimal balance) {

        public static CashClosingResponse from(CashClosing closing) {
            BigDecimal balance = closing.getPixTotal()
                    .add(closing.getCardTotal())
                    .add(closing.getCashTotal())
                    .subtract(closing.getExpenseTotal());
            return new CashClosingResponse(closing.getId(), closing.getAccountId(), closing.getClosingDate(),
                    closing.getPixTotal(), closing.getCardTotal(), closing.getCashTotal(),
                    closing.getExpenseTotal(), balance);
        }
    }

    public record ProfitResponse(BigDecimal revenue, BigDecimal costOfGoods, BigDecimal expenses,
            BigDecimal profit) {
    }

    public enum ProfitGroup {
        DAY, PRODUCT, CATEGORY
    }

    @Relation(collectionRelation = "lines")
    public record ProfitLine(String key, BigDecimal revenue, BigDecimal costOfGoods, BigDecimal profit) {
    }
}
