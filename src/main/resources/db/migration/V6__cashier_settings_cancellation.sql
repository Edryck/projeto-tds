insert into permission (id, code, description, created_at, updated_at, changed_at) values
    ('50000000-0000-0000-0000-000000000019', 'order:request-cancel', 'Solicitar cancelamento de pedido', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000020', 'settings:manage', 'Gerenciar configurações da loja', now(), now(), now());

insert into role_permission (role_id, permission_id) values
    ('10000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000019'),
    ('10000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000020');

insert into role (id, name, created_at, updated_at, changed_at) values
    ('10000000-0000-0000-0000-000000000002', 'CAIXA', now(), now(), now());

insert into role_permission (role_id, permission_id)
select '10000000-0000-0000-0000-000000000002', id from permission
where code in ('product:view', 'product:edit', 'stock:view', 'stock:adjust', 'customer:view', 'customer:edit',
    'order:view', 'order:create', 'order:pay', 'order:ship', 'order:request-cancel', 'finance:view',
    'finance:edit', 'audit:view', 'sync:use');

create table store_settings (
    id uuid not null,
    name varchar(150) not null,
    phone varchar(20),
    email varchar(255),
    address varchar(300),
    logo bytea,
    logo_content_type varchar(50),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_store_settings primary key (id)
);

insert into store_settings (id, name, created_at, updated_at, changed_at) values
    ('70000000-0000-0000-0000-000000000001', 'Chic Charmosa', now(), now(), now());

create table cancellation_request (
    id uuid not null,
    order_id uuid not null,
    reason varchar(500) not null,
    requested_by uuid not null,
    status varchar(20) not null,
    decided_by uuid,
    decided_at timestamptz,
    response varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    changed_at timestamptz not null,
    deleted_at timestamptz,
    constraint pk_cancellation_request primary key (id),
    constraint fk_cancellation_request_order foreign key (order_id) references customer_order (id),
    constraint fk_cancellation_request_requested_by foreign key (requested_by) references app_user (id),
    constraint fk_cancellation_request_decided_by foreign key (decided_by) references app_user (id),
    constraint ck_cancellation_request_status check (status in ('PENDING', 'APPROVED', 'REJECTED'))
);

create index ix_cancellation_request_order on cancellation_request (order_id);
create index ix_cancellation_request_status on cancellation_request (status);
create unique index uk_cancellation_request_pending on cancellation_request (order_id) where status = 'PENDING';
