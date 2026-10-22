# Fase F — Comercial (notas da implementação)

Implementada em 2026-10-22 sobre a decisão comercial aprovada pelo Renner.
Onde este documento e o `PLANO-ELEVACAO.md` divergirem, **vale a decisão**:
o plano original previa `plan_limits` quantitativos + congelamento (R11.1);
a decisão aprovada os substituiu por **grátis generoso, sem limites**.

## A decisão (não reabrir)

- **Grátis GENEROSO, sem limites quantitativos** de hábitos/tarefas/vistas.
  Nada do que o usuário já usa trava, congela ou some.
- **Pro = superfícies avançadas** (lista abaixo) — só o que existe no código.
- **Preço:** assinatura anual **R$ 119,90/ano com 7 dias de teste grátis**.
  O preço EXIBIDO na UI vem **sempre** do `ProductDetails` da loja — nunca
  hardcoded (cartão da aba Mais e paywall incluídos).
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
  gating), modelos neutros da loja (`StoreProduct`/`StorePurchase`),
  `grantsPro()` e o redutor da tela de compra (`reduceBilling`) — tudo
  testado em `EntitlementTest` (gates, matriz de desafios, transições de
  estado, manager com loja falsa).
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
    `androidMain`). Fluxo completo: conectar → `queryProductDetails` →
    `launchBillingFlow` (oferta do plano-base) → listener de compras →
    `acknowledgePurchase` → entitlement ativo. Restauração via
    `queryPurchasesAsync` (abertura do app + botão no paywall).
    Contexto/Activity chegam por `initBillingContext` (MainActivity),
    mesmo padrão do `NotificationScheduler`.
  - **iOS**: **sem StoreKit** (conta Apple Developer adiada pelo Renner).
    O gateway responde "loja indisponível" e o paywall mostra o estado
    honesto "em breve" — nenhuma compra é fingida.
- **Paywall** (`ui/paywall/PaywallScreen.kt`): visual do protótipo aprovado
  (roxo profundo, cartão do plano, checks) + preço real da loja, termos do
  trial, seções de venda **por recurso** (R11.2, páginas com demonstração
  visual), botão **Restaurar compra** e estados honestos de
  loading/erro/indisponível (build fora da Play = "ainda não disponível",
  sem preço inventado).
- **Copy corrigida pela decisão** (desvios conscientes do protótipo):
  - Saiu o benefício "Backup na nuvem": o recurso **não existe** (sync é
    `CLOUD_SOON`, Supabase do Constância ainda não criado). Anunciar
    recurso inexistente num paywall que cobra é problema de política da
    Play e de confiança. Volta à copy quando a nuvem existir.
  - Saíram "Relatórios sem limite de tempo" e o rodapé "últimos 30 dias":
    contradizem o grátis generoso aprovado (todos os períodos são grátis).
  - Saíram a equivalência mensal e o "Economize 37%": só existe o plano
    anual; não há produto mensal para comparar.

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

## IDs do produto (usados no código — criar IDÊNTICOS no Console)

| Item | ID / valor |
|---|---|
| Produto de assinatura | `constancia_pro` |
| Plano-base | `anual` (cobrança anual, renovação automática) |
| Preço do plano-base | R$ 119,90 |
| Oferta | `trial-7-dias` — teste grátis de 7 dias, elegível a novos assinantes |

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
   - Plano-base: ID `anual`, período de cobrança **anual**, renovação
     automática, preço **R$ 119,90** (Brasil);
   - Oferta no plano-base: ID `trial-7-dias`, fase de **teste grátis de
     7 dias**, elegibilidade "novos assinantes"; ativar plano-base e oferta.
4. **License testers**: Configurações → Teste de licença → adicionar os
   e-mails Google que vão testar (compram sem cobrança real). Incluir o
   e-mail da conta usada no aparelho de teste.
5. **AAB na trilha de teste fechado**: gerar o AAB assinado de release,
   subir em Teste → Teste fechado, criar a lista de testadores, aguardar
   a revisão/publicação da trilha e **aceitar o convite** no aparelho
   (o app precisa ser INSTALADO PELA PLAY — sideload do APK de debug não
   vende assinatura).
6. **Testar a compra** com a conta license tester: abrir o paywall (o
   preço deve vir da loja), assinar com teste grátis, conferir o
   desbloqueio imediato, depois cancelar em Play Store → Assinaturas e
   conferir o retorno do cadeado na próxima abertura do app.
7. Requisitos atuais do Google para contas pessoais novas (teste fechado
   com 12+ testadores por 14 dias antes da produção) seguem valendo para
   a publicação aberta — a trilha fechada acima já serve para isso.

## O que foi provado aqui (e o que NÃO foi)

- ✅ Código compila (Android + iOS), CI verde nos 4 jobs.
- ✅ Lógica de gating e de entitlement testada (unitários, loja falsa):
  upgrade ativa e reconhece a compra; downgrade verificado devolve o
  cadeado; loja inalcançável mantém o cache; compra pendente não ativa.
- ✅ Paywall exibe estados honestos sem loja (teste de UI no emulador).
- ❌ **Compra de ponta a ponta NÃO testada** — impossível sem o Play
  Console configurado (checklist acima). Nenhuma evidência neste repo
  deve ser lida como "a venda funciona"; o que funciona é o código do
  fluxo, pronto para o primeiro teste real na trilha fechada.
