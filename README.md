# Chic Charmosa — ERP/CRM

API REST de gestão (estoque, clientes, pedidos e financeiro) para a loja Chic Charmosa, com sincronização offline. Projeto do Exame de Suficiência de Tecnologia em Desenvolvimento de Sistemas (UTFPR).

## Descrição
[PREENCHER com suas palavras: o problema da loja, o que o sistema resolve e como funciona a sincronização offline. Detalhes técnicos estão no PRODUCT.md.]

## Tecnologias
- Java 21, Spring Boot 3.5, Maven
- Spring Data JPA (Hibernate) e PostgreSQL
- Flyway (versionamento do banco)
- Spring Security com JWT
- Spring HATEOAS
- springdoc-openapi (Swagger)
- Docker Compose (PostgreSQL local)
- Insomnia (testes dos endpoints)
- Frontend (fase 2): React, Vite, TypeScript, PWA

## Pré-requisitos
- JDK 21 (ou 17)
- Docker e Docker Compose
- Insomnia

## Configuração do banco de dados
1. Copie `.env.example` para `.env` e ajuste usuário, senha e nome do banco.
2. Suba o PostgreSQL: `docker compose up -d`
3. Não precisa criar tabelas: o Flyway cria a estrutura e insere os dados básicos quando a API sobe.

## Como executar a API
```bash
./mvnw spring-boot:run
```
- API: `http://localhost:8080`
- Swagger (só em ambiente de desenvolvimento): `http://localhost:8080/swagger-ui.html`

## Usuário inicial
| E-mail | Senha |
|---|---|
| [PREENCHER] | [PREENCHER] |

Criado pela migration `V2`. Troque a senha em qualquer uso real.

## Autenticação e uso do token JWT
1. `POST /api/auth/login` com e-mail e senha.
2. A resposta traz um token de acesso (15 minutos) e um refresh token (7 dias).
3. Envie o token de acesso nas demais requisições: `Authorization: Bearer <token>`.
4. Sem token ou com token inválido a API responde `401`. Com token válido mas sem a permissão necessária, `403`.
5. `POST /api/auth/refresh` renova o token de acesso.

## Testes dos endpoints
Importe `insomnia/chic-charmosa.insomnia.json` no Insomnia. A coleção é organizada por recurso e usa uma variável `token` preenchida pelo request de login. Rode primeiro a pasta de autenticação.

## Frontend e integração
[PREENCHER na fase 2: como rodar o frontend, como ele guarda e envia o token e como faz a sincronização offline.]

## Modelo de Maturidade de Richardson
[PREENCHER: resumo dos níveis 1, 2 e 3 e onde ver as evidências no código.]

## Documentação das rotas
[PREENCHER: tabela de rotas, ou link para o Swagger.]

## Créditos e referências
- [PREENCHER: documentação oficial, tutoriais e templates que você realmente usou]

## Autor
[Seu nome] — Tecnologia em Desenvolvimento de Sistemas, UTFPR
