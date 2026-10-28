package br.com.chiccharmosa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cash_closing")
public class CashClosing extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "pix_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal pixTotal;

    @Column(name = "card_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal cardTotal;

    @Column(name = "cash_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashTotal;

    @Column(name = "expense_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal expenseTotal;

    @Column(name = "counted_pix", precision = 12, scale = 2)
    private BigDecimal countedPix;

    @Column(name = "counted_card", precision = 12, scale = 2)
    private BigDecimal countedCard;

    @Column(name = "counted_cash", precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(length = 1000)
    private String note;

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public LocalDate getClosingDate() {
        return closingDate;
    }

    public void setClosingDate(LocalDate closingDate) {
        this.closingDate = closingDate;
    }

    public BigDecimal getPixTotal() {
        return pixTotal;
    }

    public void setPixTotal(BigDecimal pixTotal) {
        this.pixTotal = pixTotal;
    }

    public BigDecimal getCardTotal() {
        return cardTotal;
    }

    public void setCardTotal(BigDecimal cardTotal) {
        this.cardTotal = cardTotal;
    }

    public BigDecimal getCashTotal() {
        return cashTotal;
    }

    public void setCashTotal(BigDecimal cashTotal) {
        this.cashTotal = cashTotal;
    }

    public BigDecimal getExpenseTotal() {
        return expenseTotal;
    }

    public void setExpenseTotal(BigDecimal expenseTotal) {
        this.expenseTotal = expenseTotal;
    }

    public BigDecimal getCountedPix() {
        return countedPix;
    }

    public void setCountedPix(BigDecimal countedPix) {
        this.countedPix = countedPix;
    }

    public BigDecimal getCountedCard() {
        return countedCard;
    }

    public void setCountedCard(BigDecimal countedCard) {
        this.countedCard = countedCard;
    }

    public BigDecimal getCountedCash() {
        return countedCash;
    }

    public void setCountedCash(BigDecimal countedCash) {
        this.countedCash = countedCash;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
