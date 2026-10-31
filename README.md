# Chic Charmosa — ERP/CRM

API REST de gestão (catálogo, estoque, clientes, pedidos e financeiro) para a loja de roupas Chic Charmosa, com aplicação web em React que consome a API. Projeto do Exame de Suficiência de Tecnologia em Desenvolvimento de Sistemas (UTFPR).

## Descrição

A Chic Charmosa é uma loja pequena que controlava estoque, clientes, vendas e dinheiro em anotações e planilhas separadas. O sistema junta tudo em um lugar só:

- **Catálogo**: categorias, produtos e variações (cor, tamanho, modelo) com preço e custo.
- **Estoque**: o saldo de cada variação é a soma das movimentações (entradas, vendas, ajustes). Nunca é editado direto.
- **Fornecedores e clientes**: cadastro com endereços e histórico de pedidos.
- **Vendas**: pedido com itens, cupom e frete. Segue a máquina de estados `PENDING → PAID → SHIPPED → DELIVERED`, com `CANCELLED` a partir de `PENDING` ou `PAID`. Quem não pode cancelar pede o cancelamento e um administrador aprova ou rejeita.
- **Financeiro**: lançamentos de receita e despesa, categorias, fechamento de caixa e relatórios de lucro e de vendas por forma de pagamento.
- **Administração**: usuários, perfis com permissões, configurações da loja e auditoria de tudo que muda.
- **Sincronização**: `POST /api/sync/push` e `GET /api/sync/pull` aceitam operações feitas offline. São idempotentes pelo UUID e uma venda offline sempre é aceita, gerando alerta de divergência de estoque se faltar saldo. O frontend atual fala com a API online; o armazenamento offline fica para uma próxima fase.

Regras de negócio, entidades e decisões técnicas detalhadas estão no [PRODUCT.md](PRODUCT.md). Os fluxos do app, em diagramas, estão em [docs/app-flow.md](docs/app-flow.md). O roteiro dos vídeos e a ordem dos testes estão em [docs/video-scripts.md](docs/video-scripts.md).

## Tecnologias

**API**
- Java 21, Spring Boot 4.1.1, Maven
- Spring Web MVC e Bean Validation
- Spring Data JPA (Hibernate) e PostgreSQL 16
- Flyway (versionamento do banco)
- Spring Security com JWT (JJWT)
- Spring HATEOAS (HAL)
- springdoc-openapi (Swagger UI)
- Docker Compose (PostgreSQL local)

**Frontend (`web/`)**
- React 18, TypeScript, Vite
- React Router 6
- Tailwind CSS 3
- lucide-react (ícones)

**Testes**
- Coleção do Insomnia em `insomnia/chic-charmosa.insomnia.json`
- Testes automatizados com JUnit em `src/test` (estoque, idempotência do push, estados do pedido, permissões, Flyway)

## Pré-requisitos

- JDK 21
- Docker e Docker Compose
- Node.js 20 ou mais novo e npm (para o frontend)
- Insomnia

## Configuração do banco de dados

1. Copie `.env.example` para `.env` e ajuste usuário, senha, porta e nome do banco:
   ```bash
   cp .env.example .env
   ```
2. Suba o PostgreSQL:
   ```bash
   docker compose up -d
   ```
   O container usa a porta de `DB_PORT` (padrão `5432`). Se mudar a porta no `.env`, a API usa a mesma.
3. Não precisa criar tabelas. O Flyway roda sozinho quando a API sobe:

| Migration | O que faz |
|---|---|
| `V1__create_schema.sql` | 26 tabelas com chaves primárias, estrangeiras, unicidade e checks |
| `V2__seed_basic_data.sql` | permissões, perfil `ADMIN`, usuário administrador, contas e categorias financeiras |
| `V3` a `V6` | cliente padrão, contato do fornecedor, notas financeiras, caixa e cancelamento |
| `V7__single_developer_admin.sql` | troca e-mail, nome e senha do administrador inicial |

O Hibernate roda com `ddl-auto=validate`: o schema é sempre do Flyway.

## Como executar a API

```bash
./mvnw spring-boot:run
```

- API: `http://localhost:8080`
- Swagger UI (ligado por padrão): `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Para desligar o Swagger, defina `SWAGGER_ENABLED=false` no `.env`.

Testes automatizados (precisam do PostgreSQL no ar):

```bash
./mvnw test
```

## Como executar o frontend

```bash
cd web
cp .env.example .env
npm install
npm run dev
```

- App: `http://localhost:5173`
- `VITE_API_URL` em `web/.env` aponta para a API (padrão `http://localhost:8080`).
- A API libera CORS para a origem de `CORS_ORIGIN` (padrão `http://localhost:5173`).
- Build de produção: `npm run build`.

## Usuário inicial

| E-mail | Senha |
|---|---|
| `admin@admin.dev` | `admin123` |

Usuário "Desenvolvedor", único usuário do seed. Criado pela `V2` e ajustado pela `V7`, com o perfil `ADMIN` (todas as permissões). Troque a senha em qualquer uso real.

## Autenticação e uso do token JWT

1. `POST /api/auth/login` com `email`, `password`, `deviceId` (UUID gerado pelo cliente) e `deviceName`.
2. A resposta traz `accessToken` (JWT, 15 minutos) e `refreshToken` (7 dias, guardado com hash no banco e revogável).
3. Envie o token de acesso nas demais requisições: `Authorization: Bearer <accessToken>`.
4. `POST /api/auth/refresh` com o `refreshToken` devolve um novo token de acesso. `POST /api/auth/logout` revoga a sessão.
5. `GET /api/auth/me` devolve o usuário, o perfil e as permissões.
6. Sem token ou com token inválido a API responde `401`. Com token válido sem a permissão do endpoint, `403`.

Controle de acesso: cada endpoint exige uma **permissão** (`product:edit`, `finance:view`, `order:pay`...) com `@PreAuthorize`, nunca um nome de perfil. Perfis são conjuntos de permissões e podem ser criados e editados em `/api/roles`.

### No frontend

- O token de acesso fica só em memória (`web/src/api.ts`). O refresh token fica no `localStorage`.
- Toda chamada passa por `request()` em `web/src/api.ts`, que coloca o `Authorization: Bearer`.
- Em `401` o frontend tenta `refresh` uma vez e repete a chamada. Se falhar, volta para o login.
- O menu e as ações da tela aparecem conforme as permissões de `/api/auth/me` e os `_links` da resposta.
- Erros da API (`detail` e `errors` por campo) viram mensagens e toasts na tela.

## Integração frontend e API

```
web/src
├── api.ts        cliente HTTP, token, refresh e tratamento de erro
├── auth.tsx      contexto de sessão e permissões
├── hooks.ts      hooks de consulta e paginação
├── types.ts      tipos das respostas da API
├── components/   layout, toasts, seletores
└── pages/        catalog, stock, suppliers, customers, sales, finance, reports, admin
```

Os recursos chegam em HAL: o frontend lê `_links` (por exemplo `pay`, `cancel`, `ship`) para decidir quais botões mostrar. O pedido `PENDING` mostra pagar e cancelar. O `DELIVERED` não mostra ação nenhuma.

## Testes dos endpoints

Importe `insomnia/chic-charmosa.insomnia.json` no Insomnia e suba a API antes.

- Uma pasta por recurso (Auth, Roles, Users, Categories, Products, Variants, Stock, Suppliers, Customers, Orders, Coupons, Finance, Audit, Cancellations, Store Settings) com cadastro, listagem, busca por ID, atualização e exclusão.
- A variável `token` do ambiente é preenchida pelo login do administrador (`Auth > POST /api/auth/login`). Todas as outras requisições enviam `Bearer {{ _.token }}`.
- **Security**: provas de `401` (sem token, token inválido) e de `403` (usuário `VIEWER`, que só tem `product:view`). Rode a pasta de cima para baixo: ela cria o perfil e o usuário e faz o login dele.
- **Flow - Sale**: categoria, produto, variação, entrada de estoque, cliente, pedido, pagamento, envio e entrega, conferindo saldo, movimentações e financeiro.
- **Flow - Cancellation**: cria um pedido e cancela, devolvendo o estoque. Rode depois do Flow - Sale, porque usa a variação e o cliente criados lá.
- **Flow - Catalog CRUD**: ciclo completo de fornecedor, terminando em `404` depois da exclusão.

Cada passo guarda o ID criado pelo passo anterior (response chaining do Insomnia), então rode as pastas de cima para baixo. Os fluxos geram UUIDs novos a cada execução e podem ser repetidos.

A coleção é gerada por `scripts/generate_insomnia.py` a partir do Swagger:

```bash
python3 scripts/generate_insomnia.py --api-docs http://localhost:8080/v3/api-docs --out insomnia/chic-charmosa.insomnia.json
```

## Modelo de Maturidade de Richardson

- **Nível 0**: superado. Não existe um endpoint único que recebe tudo por POST.
- **Nível 1 — recursos**: cada coisa tem sua URI: `/api/orders`, `/api/orders/{id}`, `/api/customers/{id}/addresses`, `/api/products/{productId}/variants`.
- **Nível 2 — verbos e status HTTP**: `GET` consulta, `POST` cria (`201` com `Location`), `PUT` atualiza, `PATCH` altera parte, `DELETE` exclui (`204`). Erros usam `400` (validação), `401`, `403`, `404`, `409` (conflito) e `422` (regra de negócio), no formato Problem Details.
- **Nível 3 — HATEOAS**: as respostas são HAL com `_links`, e os links de ação mudam com o estado do recurso. Um pedido `PENDING` traz `pay` e `cancel`. Depois de pago, traz `ship`. Depois de entregue, só `self`, `orders` e `customer`. Os links também respeitam as permissões de quem pede.

Onde ver as evidências:
- Código: `src/main/java/br/com/chiccharmosa/assembler/` (um assembler por recurso, `OrderAssembler` é o melhor exemplo) e os controllers em `controller/`.
- Execução: Insomnia, pasta `Flow - Sale`, requests `9 - get order` e `13 - get order`. Compare o campo `_links`.
- Exceção consciente: os endpoints de sincronização em lote (`/api/sync/*`) não são recursos hipermídia.

## Documentação das rotas

A lista completa e interativa está no Swagger UI (`/swagger-ui.html`). Resumo:

| Recurso | Rotas |
|---|---|
| Autenticação | `POST /api/auth/login`, `/refresh`, `/logout`, `GET /api/auth/me` |
| Perfis e usuários | `/api/roles`, `/api/roles/permissions`, `/api/users`, `/api/devices` |
| Catálogo | `/api/categories`, `/api/products`, `/api/products/{id}/variants`, `/api/variants` |
| Estoque | `/api/stock-entries`, `GET /api/variants/{id}/movements`, `POST /api/variants/{id}/adjustments` |
| Fornecedores | `/api/suppliers` |
| Clientes | `/api/customers`, `/api/customers/{id}/addresses` |
| Pedidos | `/api/orders`, `POST /api/orders/{id}/pay`, `/ship`, `/deliver`, `/cancel`, `PATCH /shipping-cost` |
| Cancelamentos | `POST /api/orders/{id}/cancellation-requests`, `/api/cancellation-requests` (`/approve`, `/reject`) |
| Cupons | `/api/coupons` |
| Financeiro | `/api/financial-accounts`, `/api/financial-categories`, `/api/financial-entries` (`/summary`), `/api/cash-closings`, `/api/finance/profit`, `/api/reports/sales-by-payment` |
| Loja | `/api/store-settings` |
| Auditoria | `/api/audit-logs` |
| Sincronização | `POST /api/sync/push`, `GET /api/sync/pull?since=` |

## Entidades

26 tabelas, todas com UUID gerado pelo cliente, `created_at`, `updated_at` e exclusão lógica: `permission`, `role`, `role_permission`, `app_user`, `device`, `refresh_token`, `category`, `product`, `product_variant`, `supplier`, `stock_entry`, `stock_entry_item`, `stock_movement`, `customer`, `address`, `coupon`, `customer_order`, `order_item`, `payment`, `shipment`, `financial_account`, `financial_category`, `financial_entry`, `cash_closing`, `audit_log`, `sync_operation`.

## Organização do código

```
src/main/java/br/com/chiccharmosa
├── controller/   rotas HTTP e permissões
├── service/      regras de negócio
├── repository/   Spring Data JPA
├── entity/       entidades JPA
├── dto/          records de Request e Response
├── assembler/    montagem HAL com _links
├── security/     filtro e serviço JWT
├── config/       segurança e CORS
└── exception/    erros em Problem Details
```

## Créditos e referências

- Documentação oficial do Spring Boot, Spring Data JPA, Spring Security e Spring HATEOAS
- Documentação do Flyway
- Documentação do JJWT
- Documentação do springdoc-openapi
- Martin Fowler, "Richardson Maturity Model"
- Documentação do React, React Router, Vite e Tailwind CSS
- Imagem da logo fornecida pela loja
- Assistente de código (Claude) usado como apoio na escrita; o código foi revisado e é explicado nos vídeos

## Autor

Edryck Nascimento — Tecnologia em Desenvolvimento de Sistemas, UTFPR
