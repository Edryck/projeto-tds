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
import jakarta.validation.constraints.PositiveOrZero;
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

    @Relation(collectionRelation = "financialCategories")
    public record FinancialCategoryView(UUID id, String name, EntryType type, long entryCount, boolean system) {
    }

    public record EntryRequest(UUID id, @NotNull UUID accountId, @NotNull UUID categoryId, @NotNull EntryType type,
            @NotNull @Positive BigDecimal amount, PaymentMethod paymentMethod, @NotNull LocalDate entryDate,
            @Size(max = 500) String description, @Size(max = 1000) String note) {
    }

    @Relation(collectionRelation = "entries")
    public record EntryResponse(UUID id, UUID accountId, UUID categoryId, EntryType type, BigDecimal amount,
            PaymentMethod paymentMethod, LocalDate entryDate, String description, UUID orderId,
            String note) {

        public static EntryResponse from(FinancialEntry entry) {
            return new EntryResponse(entry.getId(), entry.getAccountId(), entry.getCategoryId(), entry.getType(),
                    entry.getAmount(), entry.getPaymentMethod(), entry.getEntryDate(), entry.getDescription(),
                    entry.getOrderId(), entry.getNote());
        }
    }

    public record EntrySummary(BigDecimal income, BigDecimal expense, BigDecimal balance) {
    }

    public record CashClosingRequest(UUID id, @NotNull UUID accountId, @NotNull LocalDate closingDate,
            @NotNull @PositiveOrZero BigDecimal countedPix, @NotNull @PositiveOrZero BigDecimal countedCard,
            @NotNull @PositiveOrZero BigDecimal countedCash, @Size(max = 1000) String note) {
    }

    @Relation(collectionRelation = "cashClosings")
    public record CashClosingResponse(UUID id, UUID accountId, LocalDate closingDate, BigDecimal pixTotal,
            BigDecimal cardTotal, BigDecimal cashTotal, BigDecimal expenseTotal, BigDecimal balance,
            BigDecimal countedPix, BigDecimal countedCard, BigDecimal countedCash, BigDecimal pixDifference,
            BigDecimal cardDifference, BigDecimal cashDifference, BigDecimal totalDifference, String note) {

        public static CashClosingResponse from(CashClosing closing) {
            BigDecimal balance = closing.getPixTotal()
                    .add(closing.getCardTotal())
                    .add(closing.getCashTotal())
                    .subtract(closing.getExpenseTotal());
            BigDecimal pixDifference = difference(closing.getCountedPix(), closing.getPixTotal());
            BigDecimal cardDifference = difference(closing.getCountedCard(), closing.getCardTotal());
            BigDecimal cashDifference = difference(closing.getCountedCash(), closing.getCashTotal());
            BigDecimal totalDifference = null;
            if (pixDifference != null && cardDifference != null && cashDifference != null) {
                totalDifference = pixDifference.add(cardDifference).add(cashDifference);
            }
            return new CashClosingResponse(closing.getId(), closing.getAccountId(), closing.getClosingDate(),
                    closing.getPixTotal(), closing.getCardTotal(), closing.getCashTotal(),
                    closing.getExpenseTotal(), balance, closing.getCountedPix(), closing.getCountedCard(),
                    closing.getCountedCash(), pixDifference, cardDifference, cashDifference, totalDifference,
                    closing.getNote());
        }

        private static BigDecimal difference(BigDecimal counted, BigDecimal expected) {
            return counted == null ? null : counted.subtract(expected);
        }
    }

    public record CashClosingPreview(UUID accountId, LocalDate closingDate, BigDecimal pixTotal,
            BigDecimal cardTotal, BigDecimal cashTotal, BigDecimal expenseTotal, UUID closingId) {
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
