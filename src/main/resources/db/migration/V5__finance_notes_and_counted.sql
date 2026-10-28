alter table financial_entry add column note varchar(1000);

alter table cash_closing add column counted_pix numeric(12,2);
alter table cash_closing add column counted_card numeric(12,2);
alter table cash_closing add column counted_cash numeric(12,2);
alter table cash_closing add column note varchar(1000);
