insert into permission (id, code, description, created_at, updated_at, changed_at) values
    ('50000000-0000-0000-0000-000000000001', 'product:view', 'Ver produtos', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000002', 'product:edit', 'Editar produtos', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000003', 'stock:view', 'Ver estoque', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000004', 'stock:adjust', 'Ajustar estoque', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000005', 'customer:view', 'Ver clientes', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000006', 'customer:edit', 'Editar clientes', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000007', 'order:view', 'Ver pedidos', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000008', 'order:create', 'Criar pedidos', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000009', 'order:pay', 'Pagar pedidos', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000010', 'order:ship', 'Enviar pedidos', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000011', 'order:cancel', 'Cancelar pedidos', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000012', 'coupon:edit', 'Editar cupons', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000013', 'finance:view', 'Ver financeiro', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000014', 'finance:edit', 'Editar financeiro', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000015', 'audit:view', 'Ver auditoria', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000016', 'user:manage', 'Gerenciar usuários', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000017', 'role:manage', 'Gerenciar perfis', now(), now(), now()),
    ('50000000-0000-0000-0000-000000000018', 'sync:use', 'Usar sincronização', now(), now(), now());

insert into role (id, name, created_at, updated_at, changed_at) values
    ('10000000-0000-0000-0000-000000000001', 'ADMIN', now(), now(), now());

insert into role_permission (role_id, permission_id)
select '10000000-0000-0000-0000-000000000001', id from permission;

insert into app_user (id, email, password_hash, name, role_id, active, created_at, updated_at, changed_at) values
    ('20000000-0000-0000-0000-000000000001', 'admin@chiccharmosa.com.br', '$2y$10$uGcI6mItyPQvkN.LH7L78OuO4nx3t3w/S2cP.JMtFi/UxTGL9M4Pq', 'Administrador', '10000000-0000-0000-0000-000000000001', true, now(), now(), now());

insert into financial_account (id, name, type, created_at, updated_at, changed_at) values
    ('30000000-0000-0000-0000-000000000001', 'Loja', 'STORE', now(), now(), now()),
    ('30000000-0000-0000-0000-000000000002', 'Pessoal', 'PERSONAL', now(), now(), now());

insert into financial_category (id, name, type, created_at, updated_at, changed_at) values
    ('40000000-0000-0000-0000-000000000001', 'Sales', 'INCOME', now(), now(), now()),
    ('40000000-0000-0000-0000-000000000002', 'Refund', 'EXPENSE', now(), now(), now()),
    ('40000000-0000-0000-0000-000000000003', 'Shipping', 'EXPENSE', now(), now(), now()),
    ('40000000-0000-0000-0000-000000000004', 'Other', 'EXPENSE', now(), now(), now());
