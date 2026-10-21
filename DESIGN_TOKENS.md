# Constância — Design Tokens (fonte: protótipo final aprovado "Roxo Profundo")

Extraído de `~/workspace/ts-spaces/prot-tipo-const-ncia-roxo-profundo/index.html`.
REGRA: aplicar literalmente. Nada de "parecido". Este arquivo descreve o
protótipo — em divergência entre ele e o HTML, vale o HTML.
Marca: **roxo profundo predominante** sobre off-white quente; chama do streak
em âmbar (único acento quente). Cores com significado NÃO viram roxo: humor
(5 cores), projetos, prioridades (P1/P2/P3) e a cor de cada hábito.

## Paleta — app (light / dark)

| token | light | dark | uso |
|---|---|---|---|
| bg | #FBF8F3 | #160A2C | fundo das telas |
| ink | #221238 | #F4EFFC | texto principal |
| ink-2 | #4A3A63 | #C9BCE4 | texto intermediário |
| muted | #7A6B90 | #AB9AC6 | texto secundário |
| line | #E2D8F0 | #3B2566 | bordas fortes (chips, steppers) |
| line-soft | #EAE2F4 | #2E1C52 | bordas dos cards e divisórias |
| primary | #4B1D96 | #B79CFF | botões, seleção, destaques |
| primary-soft | #EBE2FA | #2E1A56 | fundo suave do primário |
| surface | #FFFFFF | #22123F | cards |
| surface-2 | #FAF6FD | #2B1850 | superfícies secundárias |
| flame | #E86A17 | #FF8A3D | streaks (chama), P2 |
| on-primary | #FFFFFF | #1E0740 | texto sobre o primário |
| tab-bg | #FFFFFF 86% | #1D0E38 94% | fundo da barra flutuante (translúcido) |
| check-border | #C9BCE0 | #4A3572 | borda do check circular vazio |
| placeholder | #9A86B8 | #8F7DB3 | placeholders de captura |

Tema escuro refeito em roxo (não é inversão): fundo roxo-noite, superfícies
violeta-escuras, primário lavanda. Painéis em gradiente (herói, insight,
ano em revista, Pro) usam as cores FIXAS do protótipo nos dois temas.

## Cores semânticas (nunca substituídas pela marca)

| significado | cores |
|---|---|
| humor 1..5 | #D64541 · #E07B2A · #E9C46A · #43A673 · #0B7A55 |
| projetos | Trabalho #2563EB · Casa #D97706 · Pessoal #0B7A55 · Saúde #DB2777 · Entrada #7A6B90 |
| prioridades | P1 #D92D20 · P2 #E86A17 · P3 #2563EB |
| perigo | #D92D20 (dark #F97066) |
| sucesso/delta | #0B7A55 (dark #34D399) |

Tints suaves (light): âmbar #FBEDD8 · chama #FDEBDC · sol #F0E8FA ·
aqua #E0F2F4 · azul #EAE1FB · verde #E0F2EA. Sólidos de ícone: âmbar
#D97706 · sol #8A5FC0 · aqua #0E7C86 · azul #6D3BD1.

## Painel-herói "Seu dia" (Hoje)
- SEMPRE gradiente roxo profundo `155deg #4B1D96 → #38106E 42% → #1E0740`,
  raio **28dp**, padding **20dp**, sombra `0 24px 44px -18px rgba(42,10,84,.55)`.
  NUNCA cartão branco. Círculos decorativos: anéis translúcidos brancos
  (250dp borda 34dp a 7%; 190dp borda 26dp a 5%).
- Topo: selo "SEU DIA" 12/800 ls .12em #C9AFF7; título Sora 21/800 branco;
  subtítulo 13/600 #D9C8F8 ("Faltam N itens pra fechar o dia"); chip de
  sequência em vidro (branco 14% + borda branco 20%, texto #DECFFB).
- Meio: anel **104dp** stroke 9dp, track branco 18%, frente **#D3BDFB**
  (lavanda — nunca âmbar), centro "%" Sora 25/800 + "DO DIA" 10.5/800 #D8C6FB.
  À direita, o breakdown em linhas com tiles translúcidos 30dp raio 10
  (branco 13%): Hábitos "2 de 4" · Tarefas "1 de 3" · Diário "por fazer".
- Rodapé: botão BRANCO largura total, raio 14, texto #2A0A54 14/800
  "Registrar o dia de hoje →" (abre o diário).
- Conta hábitos devidos + tarefas do dia + diário. Títulos por progresso:
  0% "Vamos começar" · >0 "Bom começo" · ≥50% "Quase lá" · 100% "Dia
  fechado. Constância pura." / "Tudo feito por hoje. Descanse."

## Cabeçalho da Hoje (.greet)
- Avatar 46dp raio 16 em gradiente `140deg #5B21B6 → #38106E` com sombra.
- Data 12.5/700 muted capitalizada; saudação Sora 19/700.
- Sino: botão 42dp raio 14 surface, borda line-soft, ícone 20dp ink-2,
  ponto chama 8dp quando há padrão detectado.

## Semana (.week) — pílulas, NUNCA barras nem cartão
- Linha de 7 pílulas (seg..dom), gap 7dp, sem cartão em volta.
- Pílula: raio 16, padding 9/8, surface + borda line-soft; dia 10.5/800
  muted maiúsculas; número Sora 16; pontinho 5dp sob o número (roxo;
  chama se o dia foi fechado; some se não há registro).
- Selecionada: fundo primary, textos brancos (dia #D8C6FB, ponto
  #D3BDFB), sombra `0 10px 20px -8px rgba(75,29,150,.5)`. Clicar troca o
  dia exibido (status daquele dia).

## Sessões (Hoje) — linhas em cartão único
- Cabeçalho (margem-top 20dp): tile 30dp raio 10 na cor suave da sessão
  (Manhã âmbar · Tarde chama · Noite lavanda), nome Sora 14.5/700, faixa
  ("até 12h" · "12h – 18h" · "depois das 18h") 12/700 muted, contador
  "N de M" 12/800 primary à direita.
- Itens de uma sessão vivem num ÚNICO cartão (surface, raio 24, borda
  line-soft, sombra de card) com divisórias 1dp line-soft entre linhas.
- Linha (.row): padding 13/15, tile 42dp raio 14 na cor suave PRÓPRIA do
  item (hábito: a cor dele; tarefa: a do projeto; diário: lavanda sol),
  título 14.5/700 (riscado + muted quando feito), sublinha 12/600 muted
  com a chama da sequência em flame 800 ("🔥 8 dias · todo dia") ou, na
  tarefa atrasada, "Atrasada · ontem" em perigo 800 + "· Trabalho · P1".
- Chip de horário: 12/800 ink-2, fundo surface-2, borda line-soft,
  raio 8, padding 4/8 (lembrete do hábito / hora da tarefa).
- Check: círculo **34dp**, borda 2.5dp check-border; feito = primary
  preenchido + sombra + check branco 17dp, com pulso ao alternar.
- A linha do diário fecha a sessão Noite: "Escrever no diário",
  sublinha "Pergunta do dia: “…”" e a ação "Abrir" (fundo primary-soft,
  texto primary 12/800, raio 9).
- Cartão de padrão (.insight) no fim: cabeçalho "Padrão detectado" +
  link "Ver relatórios"; cartão em gradiente `150deg #3B1478 → #22094A`,
  raio 24, padding 18; selo 11/800 ls .12em #B794F0; título Sora 18.5/800
  branco; texto 13.5/500 #DCCFFB lh 1.55.

## Tipografia
- **Sora** (ExtraBold 800 / Bold 700): títulos de tela 23/800 (21/800 nas
  telas filhas com backrow), títulos de card 16/700, valores de KPI 23/800.
- **Manrope** (Regular 400 / SemiBold 600 / Bold 700 / ExtraBold 800):
  todo o resto. Corpo 15/400; notas 12.5/600 muted.
- Cabeçalho de tela filha (.backrow): voltar 40dp raio 13 (surface,
  borda line-soft, sombra leve) + título Sora 21/800 + subtítulo
  12.5/700 muted.

## Formas e espaçamento
- Tela: padding horizontal **20dp**, topo 10dp; fundo da rolagem **118dp**
  (respiro da barra flutuante).
- Cards: fundo surface, borda 1dp line-soft, raio **24dp** (KPI 20dp,
  menu da Mais 18dp), padding 15–19dp. Sombra de card:
  `0 1px 2px rgba(38,16,74,.05), 0 10px 28px -14px rgba(38,16,74,.16)`.
- Botão primário (.btn-primary): largura total, raio **15dp**, padding
  vertical **15dp**, fundo primary, texto branco Manrope 15/800, sombra
  `0 14px 26px -10px rgba(75,29,150,.5)`.
- Botão secundário/ghost: borda 1.5dp line, fundo bg, texto ink
  Manrope 13/700, raio 12dp, padding 10/14dp.
- Segmentado (Relatórios): fundo #E4D9F5, raio 14, padding 4; item raio
  11, 13.5/800 muted; selecionado fundo surface, texto ink, sombra
  `0 3px 10px rgba(38,16,74,.14)`.
- Sheet (bottom): surface, raio **28dp** só no topo, padding 10/22/24dp,
  alça 42×5dp. Opções (.opt): tile 44dp raio 14 suave, título 15/800,
  sub 12.5/600 muted, divisórias line-soft.
- Stepper (quantidade): pílula surface, borda 1.5dp line, padding 3dp;
  botões 30dp surface-2, valor 13/800. Steppers do diário (.step-btn):
  38dp raio 12 surface-2 borda line, glifo primary 19/800; valor Sora
  17/800 (energia "4/5", sono "7h10", passo 10min, 4h–10h).

## Bottom navigation — FLUTUANTE, nunca barra plana
- Pílula destacada: margens laterais 14dp, fundo 12dp, fundo tab-bg
  translúcido, borda 1dp rgba(38,16,74,.07), raio **28dp**, sombra
  `0 16px 38px -12px rgba(38,16,74,.28)`, padding 9/6/7. Fica SOBRE o
  conteúdo (sem blur: RenderEffect exige API 31+, app é 24+).
- Aba: coluna, ícone 23dp, label 10.5/800; muted fora, primary na ativa
  + barrinha indicadora 22×3.5dp raio 99 ACIMA do ícone.
- Selo de atrasadas na aba Tarefas: círculo 17dp #D92D20, texto branco
  10/800, borda 2dp branca.
- FAB central: **54dp squircle raio 19**, gradiente `150deg #5B21B6 →
  #38106E`, elevado **−26dp** sobre a barra, sombra
  `0 14px 26px -8px rgba(56,16,110,.55)`, ícone + 26dp branco.
- Ícones das abas: Hoje = casa, Tarefas = lista, Hábitos = chama,
  Mais = três pontos (traço/outline).

## Tarefas
- Título Sora 24/800 + resumo dinâmico "N atrasadas · M para hoje"
  13.5/600 muted.
- Captura rápida: pílula surface, borda 1.5dp line, raio 16, "+" chama
  19dp, placeholder #9A86B8 "Capturar uma tarefa…", botão "Adicionar"
  primary raio 11 (13/800 branco).
- Legenda: linha única flexível 11.5/700 muted, pontos 9dp
  ("P1 alta · P2 média · P3 baixa · Trabalho · Casa · Pessoal · Saúde").
- Chips de visão: raio 99, padding 9/14, 13.5/800 ink-2, surface +
  borda 1.5dp line; contador em selo (fundo bg, 11.5 muted).
  **Selecionado = fundo ink #221238, texto branco**, selo branco 16%.
- Rótulo de grupo: Sora 13/700, margem 20/2/10, contador 12/800 muted à
  direita; "Atrasadas" em perigo com relógio 15dp.
- Linha (.trow) dentro do cartão da seção: padding 12/15; bolinha 23dp
  borda 2.2dp na cor da prioridade COM fundo tint dela (feita = primary
  + check 13dp); título 14.5/600; meta 11.5/700 muted (data — perigo se
  atrasada, primary se hoje —, ponto+projeto, sub-barra 38×5dp,
  recorrência, selo "marca o hábito X" em primary-soft).
- Notas do protótipo: na visão Hoje, "Toque numa tarefa para ver
  subtarefas, prioridade e reagendamento."; nas Feitas, "Histórico
  completo de 90 dias no Constância Pro."

## Hábitos
- Subtítulo: "Constância se constrói um dia de cada vez".
- KPIs 2×2 (raio 20, padding 15): tile de ícone 34dp raio 11 suave,
  valor Sora 23/800, legenda 12/700 muted. Cabeçalho "Seus hábitos" +
  "N ativos · mapa de 18 semanas".
- Cartão do hábito (raio 24, padding 17): tile 46dp raio 15 na cor do
  hábito, nome Sora 15.5/700, sub 12.5/600 muted ("Seg, qua e sex ·
  sessão da tarde"), check 38dp à direita.
- Goalbar: chama + "N dias de sequência" 12/800 flame à esquerda,
  "meta da semana · X de Y" muted à direita; barra 8dp fundo line,
  preenchimento na COR DO HÁBITO.
- Heatmap: grade 7 linhas × 18 semanas, células flexíveis raio 3dp,
  gap 3dp; vazia #E7DEF5; feita = cor do hábito; hoje com contorno ink
  2dp. Rótulos "há 18 semanas ↔ hoje" 10.5/700 muted.
- Botão ghost "+ Criar novo hábito": surface-2, borda 1.5dp line,
  raio 15, 14/800 ink-2, largura total.

## Diário
- Backrow "Diário" + "{data} · N dias seguidos escrevendo".
- Cartão do editor (raio 24, padding 18): "Como foi o seu dia?" Sora
  16/700 + "Toque numa carinha — dá pra ajustar depois." 13/600 muted.
- Carinhas (.moods): 5 botões flex, raio 18, borda 1.5dp line-soft,
  fundo surface-2, rosto 30dp na cor do humor, label 11/800 muted;
  selecionada: borda na cor do humor, fundo surface, sobe 3dp + sombra.
- Etiquetas: pílula raio 99, padding 8/13, borda 1.5dp line, 13/700
  ink-2; ativa = fundo primary-soft, borda primary, texto #38106E.
- Pergunta do dia (.qcard): gradiente `150deg #FAF5FE → #F3EDFC`,
  borda #D9CCF2, raio 24, padding 17; selo 11/800 ls .1em #8A5FC0 +
  "Trocar"; pergunta Sora 17/700 #341664; resposta em campo branco 75%
  raio 14 borda #D9CCF2, DENTRO do cartão.
- Salvar: .btn-primary "Salvar registro de hoje".
- Calendário: cabeçalho "{Mês} pelo seu humor" + "média X,X"; grade
  7 col gap 5dp, dias em quadrados raio 11 fundo surface-2; com
  registro = cor do humor (texto branco; humor 3 = texto ink); hoje =
  contorno 2.5dp primary; legenda das 5 cores.
- Registros recentes: cartão único com divisórias; miniatura 52dp
  raio 14 (foto ou tile do humor), título 14.5/700, sub
  "“trecho…” · energia 5 · sono 7h45".

## Relatórios
- Backrow "Relatórios" + "tarefas × hábitos × diário, cruzados".
- KPIs 2×2 como em Hábitos (sem ícone): delta em verde à frente da
  legenda ("+9 p.p. de conclusão").
- Padrões cruzados: cabeçalho fora do cartão + "só no Constância";
  cartão insight (gradiente acima) com 3 caixas .vs translúcidas
  (branco 9%, borda branco 13%, raio 15, valor Sora 21, legenda 11.5/700
  #BEA3F2) e a linha "Base: …" em #A58BD8.
- Revisão: cartão raio 24 padding 18, chip de delta (primary sobre
  primary-soft, "▲ 12%"); pares de barras 13dp (atual primary, anterior
  #D5C9E8) em área de 88dp, raios 5/5/3/3, labels 11/800 muted (hoje em
  primary); parágrafo 13/1.55 com "Vitória:" e "Lição:".
- Raio-X: parágrafo de abertura 13.5/600 ink-2; hbars (ponto + nome
  13/700, valor "9 · 47%" 12.5/800 muted à direita, barra 8dp abaixo na
  cor da fatia); parágrafo final 13/1.55 ink-2.
- Ano em Revista (.year-card): gradiente `140deg #2E1159 → #4B1D96 55%
  → #7C3AED`, raio 24, padding 19; ano fantasma Sora 104 branco 9% no
  canto inferior direito; selo #C4A6F5; título 19/800; texto 13.5
  #E0D3F7 (máx 250dp).

## Mais e Paywall
- Mais: subtítulo "Tudo o resto do Constância". Perfil (raio 24,
  padding 16): avatar 52dp raio 17 gradiente, nome Sora 16, "Plano
  gratuito", chip chama "N dias". Pro (.pro-card): gradiente `145deg
  #4B1D96 → #2A0A54 70%`, raio 24, padding 19, círculo decorativo
  rgba(196,166,245,.18); selo #C4A6F5; título 19/800; preço Sora 15/800
  + riscado #A78BDB + botão branco "Conhecer". Menu "Atalhos": botões
  surface raio 18 padding 15/16, tile 40dp raio 13 suave, título 14.5,
  sub 12.5/600 muted, seta #A08FC0.
- Paywall: backrow "Constância Pro" + "para quem leva o dia a sério";
  cartão do plano (gradiente do Pro) com selo "PLANO ANUAL", "R$
  119,90 por ano", equivalência "R$ 9,99/mês" em branco forte, selo
  #C4A6F5 "Economize 37%" + riscado "R$ 191,88 no mensal"; recursos em
  cartão (padding 8/18, linhas 11/0 com divisória line-soft, tile ok
  24dp raio 8 primary-soft); primário "Assinar o Pro — R$ 119,90/ano";
  ghost "Agora não"; rodapé "No plano gratuito continuam: …".

## O que NÃO fazer
- Não usar cores "seed" genéricas do MaterialTheme.
- Não usar `Card`/`Button`/`NavigationBar` com estilo padrão — sempre
  aplicar os tokens acima explicitamente.
- Não inventar textos nem cartões: copiar os textos e a estrutura do
  protótipo literalmente (ex.: nada de cartão branco "Seu dia", semana
  em barras, barra inferior plana ou estados vazios genéricos).
