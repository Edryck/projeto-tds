create table permission (
    id uuid not null,
    code varchar(60) not null,
    description varchar(255) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_permission primary key (id),
    constraint uk_permission_code unique (code)
);

create table role (
    id uuid not null,
    name varchar(100) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_role primary key (id)
);

create unique index uk_role_name on role (name) where deleted_at is null;

create table role_permission (
    role_id uuid not null,
    permission_id uuid not null,
    constraint pk_role_permission primary key (role_id, permission_id),
    constraint fk_role_permission_role foreign key (role_id) references role (id),
    constraint fk_role_permission_permission foreign key (permission_id) references permission (id)
);

create index ix_role_permission_permission on role_permission (permission_id);

create table app_user (
    id uuid not null,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    name varchar(150) not null,
    role_id uuid not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_app_user primary key (id),
    constraint fk_app_user_role foreign key (role_id) references role (id)
);

create unique index uk_app_user_email on app_user (email) where deleted_at is null;
create index ix_app_user_role on app_user (role_id);

create table device (
    id uuid not null,
    user_id uuid not null,
    name varchar(150) not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_device primary key (id),
    constraint fk_device_user foreign key (user_id) references app_user (id)
);

create index ix_device_user on device (user_id);

create table refresh_token (
    id uuid not null,
    user_id uuid not null,
    device_id uuid not null,
    token_hash varchar(255) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_refresh_token primary key (id),
    constraint uk_refresh_token_token_hash unique (token_hash),
    constraint fk_refresh_token_user foreign key (user_id) references app_user (id),
    constraint fk_refresh_token_device foreign key (device_id) references device (id)
);

create index ix_refresh_token_user on refresh_token (user_id);
create index ix_refresh_token_device on refresh_token (device_id);

create table category (
    id uuid not null,
    name varchar(100) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_category primary key (id)
);

create table product (
    id uuid not null,
    category_id uuid not null,
    name varchar(150) not null,
    description varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_product primary key (id),
    constraint fk_product_category foreign key (category_id) references category (id)
);

create index ix_product_category on product (category_id);

create table product_variant (
    id uuid not null,
    product_id uuid not null,
    color varchar(50) not null,
    size varchar(20) not null,
    model varchar(100),
    price numeric(12,2) not null,
    cost numeric(12,2) not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_product_variant primary key (id),
    constraint fk_product_variant_product foreign key (product_id) references product (id),
    constraint ck_product_variant_price check (price >= 0),
    constraint ck_product_variant_cost check (cost >= 0)
);

create index ix_product_variant_product on product_variant (product_id);

create table supplier (
    id uuid not null,
    name varchar(150) not null,
    phone varchar(20),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_supplier primary key (id)
);

create table stock_entry (
    id uuid not null,
    supplier_id uuid,
    entry_date date not null,
    note varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_stock_entry primary key (id),
    constraint fk_stock_entry_supplier foreign key (supplier_id) references supplier (id)
);

create index ix_stock_entry_supplier on stock_entry (supplier_id);

create table stock_entry_item (
    id uuid not null,
    stock_entry_id uuid not null,
    variant_id uuid not null,
    quantity int not null,
    unit_cost numeric(12,2) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_stock_entry_item primary key (id),
    constraint fk_stock_entry_item_stock_entry foreign key (stock_entry_id) references stock_entry (id),
    constraint fk_stock_entry_item_variant foreign key (variant_id) references product_variant (id),
    constraint ck_stock_entry_item_quantity check (quantity > 0),
    constraint ck_stock_entry_item_unit_cost check (unit_cost >= 0)
);

create index ix_stock_entry_item_stock_entry on stock_entry_item (stock_entry_id);
create index ix_stock_entry_item_variant on stock_entry_item (variant_id);

create table stock_movement (
    id uuid not null,
    variant_id uuid not null,
    quantity int not null,
    type varchar(30) not null,
    reference_id uuid,
    reason varchar(255),
    occurred_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_stock_movement primary key (id),
    constraint fk_stock_movement_variant foreign key (variant_id) references product_variant (id),
    constraint ck_stock_movement_quantity check (quantity <> 0),
    constraint ck_stock_movement_type check (type in ('ENTRY', 'SALE', 'REVERSAL', 'ADJUSTMENT'))
);

create index ix_stock_movement_variant on stock_movement (variant_id);
create index ix_stock_movement_reference on stock_movement (reference_id);

create table customer (
    id uuid not null,
    name varchar(150) not null,
    phone varchar(20),
    email varchar(255),
    note varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_customer primary key (id)
);

create table address (
    id uuid not null,
    customer_id uuid not null,
    street varchar(200) not null,
    number varchar(20),
    complement varchar(100),
    district varchar(100),
    city varchar(100) not null,
    state varchar(2) not null,
    zip_code varchar(9),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_address primary key (id),
    constraint fk_address_customer foreign key (customer_id) references customer (id)
);

create index ix_address_customer on address (customer_id);

create table coupon (
    id uuid not null,
    code varchar(50) not null,
    discount_percent numeric(5,2) not null,
    valid_from timestamptz not null,
    valid_until timestamptz not null,
    usage_limit int not null,
    used_count int not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_coupon primary key (id),
    constraint ck_coupon_discount_percent check (discount_percent > 0 and discount_percent <= 100),
    constraint ck_coupon_validity check (valid_until >= valid_from),
    constraint ck_coupon_usage_limit check (usage_limit > 0),
    constraint ck_coupon_used_count check (used_count >= 0)
);

create unique index uk_coupon_code on coupon (code) where deleted_at is null;

create table customer_order (
    id uuid not null,
    customer_id uuid not null,
    status varchar(30) not null,
    coupon_id uuid,
    subtotal numeric(12,2) not null,
    discount numeric(12,2) not null,
    shipping_cost numeric(12,2) not null default 0,
    total numeric(12,2) not null,
    note varchar(1000),
    ordered_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_customer_order primary key (id),
    constraint fk_customer_order_customer foreign key (customer_id) references customer (id),
    constraint fk_customer_order_coupon foreign key (coupon_id) references coupon (id),
    constraint ck_customer_order_status check (status in ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    constraint ck_customer_order_subtotal check (subtotal >= 0),
    constraint ck_customer_order_discount check (discount >= 0),
    constraint ck_customer_order_shipping_cost check (shipping_cost >= 0),
    constraint ck_customer_order_total check (total >= 0)
);

create index ix_customer_order_customer on customer_order (customer_id);
create index ix_customer_order_coupon on customer_order (coupon_id);
create index ix_customer_order_status on customer_order (status);

create table order_item (
    id uuid not null,
    order_id uuid not null,
    variant_id uuid not null,
    quantity int not null,
    unit_price numeric(12,2) not null,
    unit_cost numeric(12,2) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_order_item primary key (id),
    constraint fk_order_item_order foreign key (order_id) references customer_order (id),
    constraint fk_order_item_variant foreign key (variant_id) references product_variant (id),
    constraint ck_order_item_quantity check (quantity > 0),
    constraint ck_order_item_unit_price check (unit_price >= 0),
    constraint ck_order_item_unit_cost check (unit_cost >= 0)
);

create index ix_order_item_order on order_item (order_id);
create index ix_order_item_variant on order_item (variant_id);

create table payment (
    id uuid not null,
    order_id uuid not null,
    method varchar(30) not null,
    amount numeric(12,2) not null,
    paid_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_payment primary key (id),
    constraint uk_payment_order unique (order_id),
    constraint fk_payment_order foreign key (order_id) references customer_order (id),
    constraint ck_payment_method check (method in ('PIX', 'CARD', 'CASH')),
    constraint ck_payment_amount check (amount >= 0)
);

create table shipment (
    id uuid not null,
    order_id uuid not null,
    method varchar(30) not null,
    tracking_code varchar(50),
    shipped_at timestamptz,
    delivered_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_shipment primary key (id),
    constraint fk_shipment_order foreign key (order_id) references customer_order (id),
    constraint ck_shipment_method check (method in ('POST_OFFICE', 'IN_PERSON'))
);

create index ix_shipment_order on shipment (order_id);

create table financial_account (
    id uuid not null,
    name varchar(100) not null,
    type varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_financial_account primary key (id),
    constraint ck_financial_account_type check (type in ('STORE', 'PERSONAL'))
);

create table financial_category (
    id uuid not null,
    name varchar(100) not null,
    type varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_financial_category primary key (id),
    constraint ck_financial_category_type check (type in ('INCOME', 'EXPENSE'))
);

create table financial_entry (
    id uuid not null,
    account_id uuid not null,
    category_id uuid not null,
    type varchar(30) not null,
    amount numeric(12,2) not null,
    payment_method varchar(30),
    entry_date date not null,
    description varchar(500),
    order_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_financial_entry primary key (id),
    constraint fk_financial_entry_account foreign key (account_id) references financial_account (id),
    constraint fk_financial_entry_category foreign key (category_id) references financial_category (id),
    constraint fk_financial_entry_order foreign key (order_id) references customer_order (id),
    constraint ck_financial_entry_type check (type in ('INCOME', 'EXPENSE')),
    constraint ck_financial_entry_payment_method check (payment_method in ('PIX', 'CARD', 'CASH')),
    constraint ck_financial_entry_amount check (amount >= 0)
);

create index ix_financial_entry_account_date on financial_entry (account_id, entry_date);
create index ix_financial_entry_category on financial_entry (category_id);
create index ix_financial_entry_order on financial_entry (order_id);

create table cash_closing (
    id uuid not null,
    account_id uuid not null,
    closing_date date not null,
    pix_total numeric(12,2) not null,
    card_total numeric(12,2) not null,
    cash_total numeric(12,2) not null,
    expense_total numeric(12,2) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_cash_closing primary key (id),
    constraint uk_cash_closing_account_date unique (account_id, closing_date),
    constraint fk_cash_closing_account foreign key (account_id) references financial_account (id)
);

create table audit_log (
    id uuid not null,
    user_id uuid not null,
    device_id uuid,
    entity varchar(30) not null,
    entity_id uuid not null,
    action varchar(30) not null,
    before_data jsonb,
    after_data jsonb,
    acted_at timestamptz not null,
    synced_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_audit_log primary key (id),
    constraint fk_audit_log_user foreign key (user_id) references app_user (id),
    constraint fk_audit_log_device foreign key (device_id) references device (id)
);

create index ix_audit_log_entity on audit_log (entity, entity_id);
create index ix_audit_log_user on audit_log (user_id);
create index ix_audit_log_acted_at on audit_log (acted_at);

create table sync_operation (
    id uuid not null,
    user_id uuid not null,
    device_id uuid not null,
    entity varchar(30) not null,
    action varchar(30) not null,
    status varchar(30) not null,
    reason varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_sync_operation primary key (id),
    constraint fk_sync_operation_user foreign key (user_id) references app_user (id),
    constraint fk_sync_operation_device foreign key (device_id) references device (id)
);

create index ix_sync_operation_user on sync_operation (user_id);
create index ix_sync_operation_device on sync_operation (device_id);
