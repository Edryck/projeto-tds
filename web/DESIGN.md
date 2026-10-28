# DESIGN.md --- Chic Charmosa

## 1. Objetivo visual

Interface de gestão para uma loja de moda chamada **Chic Charmosa**,
baseada na identidade visual da marca enviada como referência. O sistema
é um **PWA mobile-first**, pensado para uso diário por uma pessoa que
atende clientes, vende, confere estoque e acompanha o caixa pelo
celular.

A interface deve parecer uma extensão da marca: feminina sem ser
infantil, elegante sem ser luxuosa demais, acolhedora, prática e
legível. Evitar o visual genérico de SaaS, gradientes chamativos,
glassmorphism, sombras pesadas, excesso de cards e enfeites florais
espalhados por toda a interface.

## 2. Identidade visual e paleta

A imagem de referência combina fundo marfim, lettering escuro,
ornamentos florais em rosa antigo e um pequeno acento verde. Traduzir
isso para uma interface contemporânea, preservando o contraste e a
legibilidade.

  -----------------------------------------------------------------------
  Token                   Valor                   Uso
  ----------------------- ----------------------- -----------------------
  `colorPrimary`          `#382B31`               Ameixa-carvão:
                                                  navegação, títulos
                                                  fortes, texto principal

  `colorPrimaryHover`     `#4B3942`               Hover/pressed de
                                                  elementos escuros

  `colorSecondary`        `#B97891`               Rosa antigo: ações de
                                                  marca, seleção e
                                                  detalhes

  `colorSecondarySoft`    `#F1E2E8`               Fundo suave de seleção,
                                                  chips e destaques

  `colorAccent`           `#718A68`               Verde sálvia: sucesso,
                                                  estoque saudável,
                                                  confirmação

  `colorAccentSoft`       `#E5EDE1`               Fundo de estados
                                                  positivos

  `colorBackground`       `#F8F5F2`               Fundo principal marfim
                                                  rosado

  `colorSurface`          `#FFFFFF`               Formulários, listas e
                                                  superfícies principais

  `colorSurfaceAlt`       `#F0EAE7`               Áreas secundárias e
                                                  campos desabilitados

  `colorBorder`           `#E2D7D9`               Bordas e divisórias

  `colorText`             `#382B31`               Texto principal

  `colorTextSecondary`    `#786B72`               Ajuda, metadados e
                                                  rótulos secundários

  `colorError`            `#B5474B`               Erros, cancelamentos e
                                                  estoque crítico

  `colorErrorSoft`        `#F8E6E5`               Fundo de alertas de
                                                  erro

  `colorWarning`          `#A56D28`               Alertas de atenção e
                                                  pendências

  `colorWarningSoft`      `#F8EEDC`               Fundo de alertas de
                                                  atenção
  -----------------------------------------------------------------------

### Regras de cor

-   Marfim e branco dominam a interface; rosa antigo é um acento, não o
    fundo de tudo.
-   Use `colorPrimary` para texto e títulos, garantindo contraste em
    fundos claros.
-   Use rosa antigo para CTA principal, estados ativos e pequenos
    elementos de identidade. O texto sobre rosa deve ser escuro
    (`#382B31`), não branco.
-   Use verde sálvia para sucesso e confirmação; nunca dependa apenas da
    cor para comunicar um estado.
-   Vermelho significa erro, cancelamento ou estoque crítico; âmbar
    significa atenção ou pendência.
-   Não usar rosa neon, roxo saturado, dourado brilhante ou gradientes.
-   A logo original deve ser usada sem distorção, sem recoloração e sem
    recriar seus detalhes por CSS. Se não houver arquivo de logo
    disponível, usar apenas o nome "Chic Charmosa" em tipografia
    elegante como fallback, sem inventar um símbolo.

## 3. Tipografia

-   **Títulos e identidade:** serifada elegante e legível, como
    `DM Serif Display` ou `Playfair Display`.
-   **Interface, números e formulários:** sans-serif neutra, como
    `Inter` ou `Nunito Sans`.
-   Preferir fontes locais/fallbacks seguros: `Georgia, serif` para
    títulos e `Inter, Arial, sans-serif` para interface.
-   Títulos de tela: 24--28 px no mobile, peso 600--700.
-   Títulos de seção: 16--18 px, peso 600.
-   Corpo: 14--16 px; metadados nunca abaixo de 12 px.
-   Valores financeiros e totais devem usar números tabulares quando
    disponíveis, alinhados à direita.
-   Não usar fonte cursiva em botões, tabelas ou campos de formulário.

## 4. Princípios de experiência

1.  **Mobile-first real:** desenhar primeiro para 360--390 px de
    largura; expandir para tablet e desktop sem simplesmente esticar o
    layout.
2.  **Venda rápida:** o PDV é o fluxo mais importante. Adicionar
    produto, alterar quantidade, identificar cliente, escolher pagamento
    e concluir devem exigir o mínimo de passos.
3.  **Operação segura:** ações destrutivas como cancelar pedido ou
    ajustar estoque pedem confirmação e, quando necessário, motivo.
4.  **Informação clara:** preço, total, lucro, pendência e quantidade em
    estoque devem ser fáceis de localizar.
5.  **Estados explícitos:** listas precisam de estados de carregamento,
    vazio, erro e resultado filtrado vazio. Status deve ter texto/ícone
    além da cor.
6.  **Formulários simples:** rótulos persistentes, exemplos úteis,
    validação perto do campo e botões grandes.
7.  **Sem recursos fora do MVP:** não inventar 2FA, recuperação
    avançada, código de barras, marcas, coleções, pedidos de compra,
    devoluções, condicional, campanhas, fidelidade, conciliação
    bancária, auditoria ou permissões configuráveis. Os papéis Admin e
    Caixa são fixos na interface; as permissões vêm da API.
8.  **Conteúdo plausível em português brasileiro:** usar R\$, datas
    DD/MM/AAAA, horário de 24 h, vírgula decimal e termos naturais para
    uma loja brasileira.
9.  **Acessibilidade:** contraste forte, alvos de toque de pelo menos 44
    × 44 px, foco visível, campos com labels e estados que não dependam
    só de cor.
10. **PWA:** respeitar safe areas, navegação inferior fixa sem cobrir
    conteúdo, comportamento de instalação/offline apenas se o produto
    realmente implementar isso. Não simular funções inexistentes.

## 5. Estrutura de navegação

### Barra inferior fixa

Pílula flutuante com cinco destinos: **Início**, **Vendas**, **+**,
**Estoque** e **Menu** (☰). O **+** abre o sheet "O que você quer
registrar?" e substitui o botão flutuante: não existe FAB. A aba ativa
leva um ponto rosa. Considerar safe area do dispositivo e reservar
espaço inferior no conteúdo.

### Menu ☰

Gaveta com os grupos Cadastros, Financeiro, Relatórios e Administração.
Grupo sem nenhum item permitido some inteiro. Rodapé com nome, papel e
**Sair**. A partir de 1024 px a barra inferior dá lugar a uma sidebar
com o botão **Novo** e os mesmos grupos.

### Cabeçalho

-   Título claro da tela e, quando útil, subtítulo curto.
-   Ações secundárias em ícones reconhecíveis com rótulos acessíveis.
-   A logo pode aparecer no login e de forma compacta no início; não
    repetir a logo grande em cada tela.
-   Evitar cabeçalhos altos que consumam espaço útil.

## 6. Componentes

### Botão primário

-   Fundo `#B97891`, texto `#382B31`.
-   Altura de 48--52 px; raio de 12 px.
-   Peso 600; sem gradiente e sem sombra pesada.
-   Estado pressionado com escurecimento sutil e feedback imediato.

### Botão secundário

-   Fundo branco ou transparente, borda `#D7C8CE`, texto `#382B31`.
-   Mesma altura do primário.
-   Usar para cancelar, voltar ou ações alternativas sem competir com o
    CTA principal.

### Ação destrutiva

-   Texto/ícone `#B5474B`; usar botão preenchido vermelho apenas em
    confirmação final quando realmente necessário.
-   Pedir confirmação para cancelamento de pedido e ajuste que possa
    causar perda de dados.

### Cards

-   Fundo branco, borda fina `#E2D7D9`, raio de 14 px.
-   Sombra ausente ou muito discreta.
-   Padding de 14--16 px.
-   Evitar cards dentro de cards. Agrupar conteúdo por títulos e
    divisórias.

### Campos

-   Fundo branco, borda de 1 px `#D8CBCD`, raio de 10 px, altura mínima
    de 48 px.
-   Label sempre visível acima ou dentro do campo de modo persistente;
    placeholder é exemplo, não rótulo.
-   Foco com borda rosa antigo e indicador adicional.
-   Erro com texto explicativo e borda vermelha.
-   Máscaras e formatos brasileiros para moeda, telefone, CEP e CPF
    quando esses campos existirem.

### Listas e tabelas

-   No mobile, preferir linhas/cards compactos com nome, informação
    principal, status e ação contextual.
-   Tabelas tradicionais podem ser usadas em telas largas; no celular,
    converter em lista sem esconder informações essenciais.
-   Filtros importantes acessíveis sem ocupar metade da tela. Usar chips
    ou bottom sheet para filtros secundários.

### Status

-   **Pago/concluído/ativo:** verde sálvia claro + texto e/ou ícone.
-   **Pendente/aguardando:** âmbar claro + texto.
-   **Cancelado/erro/estoque crítico:** vermelho claro + texto.
-   **Rascunho/inativo:** cinza rosado claro + texto.
-   Sempre exibir o rótulo textual do status.

### Feedback

-   Toasts curtos para ações concluídas.
-   Mensagens inline para validação.
-   Confirmação explícita em ações irreversíveis.
-   Skeletons discretos durante carregamento; não usar animações
    decorativas longas.

## 7. Regras específicas por módulo

### Dashboard

Priorizar somente **vendas do dia**, **lucro** e **pedidos pendentes**.
Não acrescentar gráficos ou métricas que não foram solicitados. Mostrar
período e atualização de forma discreta.

### PDV

É a tela de maior prioridade. Busca de produto em destaque, resultados
rápidos, carrinho sempre visível, edição de quantidade e remoção
acessíveis, total muito evidente e CTA de finalizar fixo no rodapé sem
conflito com a navegação. Pagamento e conclusão devem ser um fluxo
claro. Não criar funções de devolução, orçamento ou venda condicional.

### Catálogo e estoque

Produto deve ter nome, categoria, preço de venda e variantes
necessárias. Variante pode guardar atributos como tamanho/cor e seu
preço/estoque quando aplicável. Estoque precisa distinguir posição
atual, entrada de mercadoria e ajuste com motivo. Não inventar código de
barras nem importação em lote.

### Financeiro

Usar sinal visual e texto para receita/despesa. Valores alinhados e
legíveis. Fechamento de caixa deve resumir valores esperados, valores
informados e diferença, sem afirmar integrações bancárias inexistentes.

### Relatórios

Exibir período selecionado, totais, comparações simples apenas quando os
dados existirem e opção de filtrar. DRE simplificada deve separar
receitas, custos/despesas e resultado/lucro de forma compreensível, sem
fingir ser contabilidade fiscal.

## 8. Layout responsivo

-   Mobile: padding horizontal de 16 px; espaçamento entre seções de
    20--24 px; entre itens de 8--12 px.
-   Conteúdo não deve ficar escondido atrás da barra inferior ou do FAB.
-   Formulários longos podem agrupar campos em seções, sem passos
    desnecessários.
-   Tablet: ampliar área útil e permitir duas colunas quando isso
    melhorar a leitura.
-   Desktop: conteúdo centralizado com largura máxima aproximada de 1200
    px; tabelas podem ganhar colunas, formulários podem usar duas
    colunas. Manter a mesma identidade e hierarquia.
-   Modais no mobile devem virar bottom sheets quando apropriado;
    formulários complexos merecem tela própria.

## 9. Ícones, imagens e decoração

-   Usar uma única família de ícones lineares, como Lucide.
-   Ícones de 20--24 px, com área clicável de pelo menos 44 px.
-   Elementos florais da marca são decoração pontual: login, splash ou
    pequenos detalhes promocionais. Não colocar flores atrás de dados,
    botões ou formulários.
-   Não usar fotos genéricas de modelos como fundo de telas de gestão.
    Imagens de produtos podem aparecer no catálogo e PDV se forem
    fornecidas pelo sistema.
-   Não desenhar gráficos ilustrativos que pareçam dados reais.

## 10. Movimento

-   Transições curtas, 150--220 ms, somente quando ajudam a orientar.
-   Sem parallax, animações de entrada chamativas, partículas ou efeitos
    elásticos.
-   Respeitar `prefers-reduced-motion`.
-   Feedback de toque rápido e discreto.

## 11. Regras para geração de telas com IA

Ao gerar cada tela: 1. Seguir este arquivo como fonte de verdade visual.
2. Gerar uma tela de produto real, não um moodboard ou uma apresentação.
3. Usar português brasileiro e dados fictícios coerentes. 4. Mostrar um
viewport mobile de 390 × 844 px como referência inicial. 5. Incluir
estados e componentes relevantes para a tela pedida, sem inventar
módulos fora do escopo. 6. Manter consistência de cabeçalho, botões,
campos, cards, status, navegação e FAB com as demais telas. 7. Não
redesenhar a marca. Usar o arquivo original da logo, se estiver
disponível. 8. Quando forem geradas várias telas, preservar
rigorosamente a mesma paleta, tipografia, espaçamentos e navegação.

## 12. Critérios de aceite visual

-   A tela parece pertencer à Chic Charmosa, não a um template genérico
    de administração.
-   Texto e controles são legíveis em celular.
-   A ação principal é óbvia e não compete com várias outras ações.
-   A barra inferior, o FAB e os botões fixos não se sobrepõem.
-   Estados vazios, erros e status são compreensíveis.
-   Não há funcionalidades cortadas do MVP.
