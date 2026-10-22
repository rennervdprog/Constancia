# Fase F — Comercial (notas da implementação)

Implementada em 2026-10-22 sobre a decisão comercial aprovada pelo Renner.
Onde este documento e o `PLANO-ELEVACAO.md` divergirem, **vale a decisão**:
o plano original previa `plan_limits` quantitativos + congelamento (R11.1);
a decisão aprovada os substituiu por **grátis generoso, sem limites**.

## A decisão (não reabrir)

- **Grátis GENEROSO, sem limites quantitativos** de hábitos/tarefas/vistas.
  Nada do que o usuário já usa trava, congela ou some.
- **Pro = superfícies avançadas** (lista abaixo) — só o que existe no código.
- **Preço:** três planos no mesmo produto — **mensal R$ 19,90**,
  **trimestral R$ 44,90** e **anual R$ 119,90 com 7 dias de teste
  grátis** (trial SÓ no anual). O preço EXIBIDO na UI vem **sempre**
  do `ProductDetails` da loja — nunca hardcoded (cartão da aba Mais
  e paywall incluídos); equivalente mensal e economia do paywall são
  calculados dos preços reais retornados pela loja.
- **Downgrade/cancelamento:** as superfícies Pro voltam ao cartão de cadeado
  (`ProLockCard`, com teaser); **nenhum dado é apagado nem escondido**;
  re-assinar destrava na hora, sem perda.

## Tabela final — grátis × Pro (como implementado)

| Superfície | Grátis | Pro |
|---|---|---|
| Hábitos, tarefas, diário, metas, lembretes, férias, exportar CSV | ✅ tudo, sem limite de itens | — |
| Relatórios: Resumo/KPIs, Evolução, Heatmap 18 semanas | ✅ | — |
| Relatórios: desempenho por hábito, padrão por dia da semana, humor | ✅ | — |
| Relatórios: revisão do período (resumo) e insights básicos | ✅ | — |
| Relatórios: períodos Semana/Mês/Ano | ✅ todos | — |
| **Padrões cruzados** (sono × hoje; manhã × humor/atrasos) | 🔒 cadeado | ✅ |
| **Raio-X de atrasos** (projeto/prioridade/horário) | 🔒 cadeado | ✅ |
| **Ano em Revista completo + revista do trimestre** | resumo do ano | ✅ completo |
| **Desafios com selo Pro** (campo `is_pro`; hoje: "30 dias de leitura") | 🔒 p/ começar | ✅ |
| Temas | — | **não existe** seleção de temas no app (só tema escuro, grátis) — nada gateado |

Detalhe de desafios: o gate vale só para **começar** um pacote Pro. Desafio
Pro já iniciado (ativo/concluído) continua usável após o downgrade —
nada do que o usuário já usa trava (regra `ProGate.canStartChallenge`).

## Como ficou o código

- **Domínio puro** (`domain/Entitlement.kt`): `ProGate` (regra única de
  gating), modelos neutros da loja (`StoreProduct`/`StorePlan`/
  `StoreOffer`/`StorePurchase`), `grantsPro()` e o redutor da tela de
  compra (`reduceBilling`) — tudo testado em `EntitlementTest` (gates,
  matriz de desafios, transições de estado, manager com loja falsa).
  **Multi-planos:** `PlanCatalog` faz o mapeamento plano → oferta
  (anual usa a oferta `trial-7-dias` se ela existir; mensal/trimestral
  usam a oferta padrão do plano-base; trial nunca vaza para fora do
  anual) e a matemática de exibição (`monthlyEquivalentMicros`,
  `savingsPercentVsMonthly`) trabalha só com os micros que a loja
  retornou.
- **Entitlement** (`billing/EntitlementManager.kt`): **fonte única da
  verdade**. A loja diz; o manager reconcilia e grava o cache em prefs
  (`is_pro`); as telas leem `entitlement.isProFlow`. É o único escritor de
  `prefs.setPro` (antes, `setPro` nunca era chamado — o Pro era cosmético).
  Reconcilia na abertura do app e no "Restaurar compra". Se a loja não
  puder ser consultada, **mantém o cache** (nunca rebaixa no escuro); se a
  loja confirmar que não há compra, grava `false` (downgrade honesto).
- **Loja** (`billing/BillingGateway.kt`, expect/actual sobre a interface
  `StoreBilling`):
  - **Android**: Google Play Billing Library 8.0.0 (`billing-ktx`, só
    `androidMain`). Fluxo completo: conectar → `queryProductDetails`
    (traduz TODAS as ofertas; o `PlanCatalog` escolhe a de cada plano)
    → `launchBillingFlow` com o offerToken **do plano escolhido** →
    listener de compras → `acknowledgePurchase` → entitlement ativo.
    Quem já assina outro plano-base do mesmo produto **troca de
    plano** pelo fluxo (`SubscriptionUpdateParams` com o token da
    compra atual, `CHARGE_FULL_PRICE`) — o entitlement é por produto,
    então a troca nunca derruba o Pro. Restauração via
    `queryPurchasesAsync` (abertura do app + botão no paywall).
    Contexto/Activity chegam por `initBillingContext` (MainActivity),
    mesmo padrão do `NotificationScheduler`.
  - **iOS**: **sem StoreKit** (conta Apple Developer adiada pelo Renner).
    O gateway responde "loja indisponível" e o paywall mostra o estado
    honesto "em breve" — nenhuma compra é fingida.
- **Paywall** (`ui/paywall/PaywallScreen.kt`): visual do protótipo aprovado
  (roxo profundo, cartão do plano, checks) + **seletor de planos**
  (Mensal · Trimestral · Anual; anual pré-selecionado com selo
  "MELHOR VALOR"; preço real da loja, equivalente mensal e economia
  calculados dos preços reais; trial mencionado só no anual), termos
  por plano, seções de venda **por recurso** (R11.2, páginas com
  demonstração visual), botão **Restaurar compra** e estados honestos
  de loading/erro/indisponível (build fora da Play = "ainda não
  disponível", sem preço inventado). O cartão Pro da aba Mais mostra
  o preço do plano de entrada (mensal, com sufixo) vindo da loja.
- **Copy corrigida pela decisão** (desvios conscientes do protótipo):
  - Saiu o benefício "Backup na nuvem": o recurso **não existe** (sync é
    `CLOUD_SOON`, Supabase do Constância ainda não criado). Anunciar
    recurso inexistente num paywall que cobra é problema de política da
    Play e de confiança. Volta à copy quando a nuvem existir.
  - Saíram "Relatórios sem limite de tempo" e o rodapé "últimos 30 dias":
    contradizem o grátis generoso aprovado (todos os períodos são grátis).
  - Saíram a equivalência mensal e o "Economize 37%": na época só
    existia o plano anual; não há produto mensal para comparar.
    **(Multi-planos, 2026-10-22):** com mensal e trimestral no
    catálogo, a equivalência mensal e a economia VOLTARAM — mas
    calculadas dos preços reais que a loja retorna
    (`savingsPercentVsMonthly` sobre os micros do `ProductDetails`),
    nunca um número decorativo digitado na UI.

## Limitação conhecida — entitlement LOCAL

**Não há backend**: o Supabase do Constância ainda não foi criado (decisão
do Renner de criar a conta depois). Portanto o entitlement é **local**:
a verdade é a conta da Google Play consultada neste aparelho, com cache
em prefs. Consequências honestas:

- Sem validação de recibo em servidor: alguém com aparelho modificado
  pode forjar o cache local. Aceitável para v1 pessoal/local-first; a
  correção futura é validar compras num backend quando o Supabase existir.
- Troca de aparelho: o entitlement se restabelece sozinho na abertura
  (consulta à loja com a mesma conta Google) ou pelo "Restaurar compra".
- O cache em prefs pode ficar "Pro" até a próxima reconciliação se a
  assinatura for cancelada com o app fechado e o aparelho ficar offline;
  na primeira abertura com loja acessível, o downgrade se aplica.

## IDs do produto e dos planos (usados no código — criar IDÊNTICOS no Console)

| Item | ID / valor |
|---|---|
| Produto de assinatura | `constancia_pro` |
| Plano-base mensal | `mensal` — cobrança **mensal (P1M)**, renovação automática, **R$ 19,90** — **SEM** oferta de trial |
| Plano-base trimestral | `trimestral` — cobrança **a cada 3 meses (P3M)**, renovação automática, **R$ 44,90** — **SEM** oferta de trial |
| Plano-base anual | `anual` — cobrança **anual (P1Y)**, renovação automática, **R$ 119,90** |
| Oferta (só no plano-base `anual`) | `trial-7-dias` — teste grátis de 7 dias, elegível a novos assinantes |

Qualquer plano-base ativo concede o Pro (o entitlement é por
produto). Os valores acima são os de CADASTRO no Console; no app, o
preço exibido vem sempre da loja.

(Constantes em `domain/Entitlement.kt` → `ConstanciaBilling`.)

## Checklist do Play Console — Renner

A compra real **só é testável depois** destes passos (a loja só vende o
que está configurado e publicado numa trilha):

1. **Conta de desenvolvedor** do Google Play Console ativa (se ainda não
   tiver: cadastro + taxa única de US$ 25).
2. **Criar o app** no Console (pacote `app.constancia`) e preencher o
   básico exigido para publicar em trilha de teste (nome, ícone,
   política de privacidade, classificação de conteúdo, público-alvo,
   declaração de segurança de dados).
3. **Produto de assinatura**: Monetizar → Assinaturas → Criar:
   - ID do produto: `constancia_pro` (exatamente este — está no código);
   - Plano-base 1: ID `mensal`, período de cobrança **mensal**,
     renovação automática, preço **R$ 19,90** (Brasil). **Não** criar
     oferta de teste grátis neste plano;
   - Plano-base 2: ID `trimestral`, período de cobrança **a cada
     3 meses**, renovação automática, preço **R$ 44,90** (Brasil).
     **Não** criar oferta de teste grátis neste plano;
   - Plano-base 3: ID `anual`, período de cobrança **anual**,
     renovação automática, preço **R$ 119,90** (Brasil);
   - Oferta **só no plano-base `anual`**: ID `trial-7-dias`, fase de
     **teste grátis de 7 dias**, elegibilidade "novos assinantes";
   - Ativar os três planos-base e a oferta.
4. **License testers**: Configurações → Teste de licença → adicionar os
   e-mails Google que vão testar (compram sem cobrança real). Incluir o
   e-mail da conta usada no aparelho de teste.
5. **AAB na trilha de teste fechado**: gerar o AAB assinado de release,
   subir em Teste → Teste fechado, criar a lista de testadores, aguardar
   a revisão/publicação da trilha e **aceitar o convite** no aparelho
   (o app precisa ser INSTALADO PELA PLAY — sideload do APK de debug não
   vende assinatura).
6. **Testar a compra** com a conta license tester: abrir o paywall
   (os três planos devem aparecer com os preços vindos da loja),
   assinar o anual com teste grátis, conferir o desbloqueio imediato,
   depois cancelar em Play Store → Assinaturas e conferir o retorno
   do cadeado na próxima abertura do app. Repetir com o mensal
   (sem trial: a cobrança é imediata no fluxo de teste) e, se
   quiser, a troca de plano (assinar um plano, voltar ao paywall
   pela loja e trocar para outro — o Pro não pode cair).
7. Requisitos atuais do Google para contas pessoais novas (teste fechado
   com 12+ testadores por 14 dias antes da produção) seguem valendo para
   a publicação aberta — a trilha fechada acima já serve para isso.

## O que foi provado aqui (e o que NÃO foi)

- ✅ Código compila (Android + iOS), CI verde nos 4 jobs.
- ✅ Lógica de gating e de entitlement testada (unitários, loja falsa):
  upgrade ativa e reconhece a compra; downgrade verificado devolve o
  cadeado; loja inalcançável mantém o cache; compra pendente não ativa.
- ✅ Multi-planos testados (unitários + UI com loja falsa):
  mapeamento plano → oferta (trial só no anual), matemática de
  exibição a partir de preços fake (equivalente mensal; economia
  50%/25%), qualquer plano ativo = Pro e troca de plano sem perder o
  Pro; no emulador, o seletor mostra os três planos com preços da
  loja falsa e trocar de plano muda o CTA e o plano lançado na compra
  (prints `17_paywall_planos`/`17_paywall_trimestral` no CI).
- ✅ Paywall exibe estados honestos sem loja (teste de UI no emulador).
- ❌ **Compra de ponta a ponta NÃO testada** — impossível sem o Play
  Console configurado (checklist acima). Nenhuma evidência neste repo
  deve ser lida como "a venda funciona"; o que funciona é o código do
  fluxo, pronto para o primeiro teste real na trilha fechada.
