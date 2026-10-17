package br.com.chiccharmosa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_entry")
public class StockEntry extends BaseEntity {

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "stockEntry", cascade = CascadeType.ALL)
    private List<StockEntryItem> items = new ArrayList<>();

    public void addItem(StockEntryItem item) {
        item.setStockEntry(this);
        items.add(item);
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<StockEntryItem> getItems() {
        return items;
    }
}
