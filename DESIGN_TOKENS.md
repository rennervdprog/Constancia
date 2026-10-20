# Constância — Design Tokens (fonte: protótipo final aprovado "Roxo Profundo")

Extraído de `~/workspace/ts-spaces/prot-tipo-const-ncia-roxo-profundo/index.html`.
REGRA: aplicar literalmente. Nada de "parecido".
Marca: **roxo profundo predominante** sobre off-white quente; chama do streak
em âmbar (único acento quente). Cores com significado NÃO viram roxo: humor
(5 cores), projetos, prioridades (P1/P2/P3) e a cor de cada hábito.

## Paleta — app (light / dark)

| token | light | dark | uso |
|---|---|---|---|
| bg | #FBF8F3 | #160A2C | fundo das telas |
| ink | #221238 | #F4EFFC | texto principal |
| muted | #7A6B90 | #AB9AC6 | texto secundário |
| line | #E2D8F0 | #3B2566 | bordas 1dp dos cards |
| primary | #4B1D96 | #B79CFF | botões, seleção, destaques |
| primary-soft | #EBE2FA | #2E1A56 | fundo suave do primário |
| surface | #FFFFFF | #22123F | cards |
| surface-2 | #FAF6FD | #2B1850 | superfícies secundárias |
| flame | #E86A17 | #FF8A3D | streaks (chama), P2 |
| on-primary | #FFFFFF | #1E0740 | texto sobre o primário |
| tab-bg | #FFFFFF 92% | #1D0E38 94% | fundo da bottom nav (translúcido) |

Tema escuro refeito em roxo (não é inversão): fundo roxo-noite, superfícies
violeta-escuras, primário lavanda.

## Cores semânticas (nunca substituídas pela marca)

| significado | cores |
|---|---|
| humor 1..5 | #D64541 · #E07B2A · #E9C46A · #43A673 · #0B7A55 |
| projetos | Trabalho #2563EB · Casa #D97706 · Pessoal #0B7A55 · Saúde #DB2777 · Entrada #7A6B90 |
| prioridades | P1 #D92D20 · P2 #E86A17 · P3 #2563EB |
| perigo | #D92D20 (dark #F97066) |

## Paleta — relatórios (extras)

| token | light | dark |
|---|---|---|
| brand-strong | #7C3AED | #A78BFA |
| heat-0 | #EAE2F4 | #2E1C52 |
| amber / amber-soft | #F0A840 / #FBEBD3 | #D97706 / #3A2A10 |
| red / red-soft | #D92D20 / #FDECEA | #F97066 / #3D1524 |
| ink-2 | #4A3A63 | #C9BCE4 |
| ink-3 | #7A6B90 | #8F7DB3 |
| page-bg | #F1EAF8 | #120722 |
| page-card | #FFFFFF | #22123F |
| page-line | #E2D8F0 | #3B2566 |

## Cartão "Seu dia" (destaque do Hoje)
- Fundo = **roxo-noite** #2A0A54 no claro (no escuro inverte: #EBE2FA com
  conteúdo #2A0A54). Conteúdo = off-white. Raio 24dp, padding 18dp,
  gap interno 16dp, margin-bottom 14dp.
- Anel: 84dp, track com 16% de opacidade, frente na cor **flame**, stroke 9dp,
  varredura animada reagindo aos checks. Conta hábitos + tarefas + diário.

## Tipografia
- **Sora** (ExtraBold 800 / Bold 700): saudação 25sp/800, título de tela 23sp/800,
  botão primário 16sp/700.
- **Manrope** (Regular 400 / SemiBold 600 / Bold 700 / ExtraBold 800): todo o resto.
- Linha de data: 13sp/700 muted. Subtítulo de tela: 13.5sp/600 muted, margin-bottom 16dp.
- Nota de card: 12.5sp/600 muted, line-height 1.5.
- Aba da bottom nav: 11sp/700.

## Formas e espaçamento
- Tela: padding horizontal **20dp**, topo 10dp, fundo 18dp.
- Cards: fundo surface, **borda 1dp line**, raios: habit-card **22dp** (padding 14dp,
  gap 13dp, margin-bottom 11dp), tile/stack-card **20dp** (padding 15-16dp),
  heat-card **24dp** (padding 18/16/14dp), setting-row **20dp** (padding 15/16dp, gap 12dp).
- Ícone de hábito: caixa **48dp**, raio **15dp**.
- Botão primário (cta): largura total, raio **18dp**, padding vertical **16dp**,
  fundo primary, texto branco Sora 16/700. Sem sombra no claro;
  sombra `0 10px 22px rgba(primary, .35)` é opcional.
- Botão secundário: borda 1.5dp line, fundo bg, texto ink Manrope 13.5/800,
  raio 12dp, padding 10/14dp.
- Chip: Manrope 13/700, padding 8/14dp, raio pill, borda 1.5dp line, fundo surface,
  texto muted. Selecionado: fundo primary-soft, texto primary, borda primary.
- Segmentado: botões flex-1, padding 9/6dp, raio 10dp, Manrope 13/700 muted;
  selecionado fundo surface com sombra leve `0 1px 4px rgba(0,0,0,.14)` e texto ink.
- Stepper: fundo surface, borda 1.5dp line, raio pill, padding 3dp.
- Sheet (bottom): surface, raio **28dp** só no topo, padding 12/22/30dp.
- FAB: **54dp** círculo, fundo primary, ícone branco, sombra
  `0 10px 22px rgba(11,122,85,.35)` (claro).

## Bottom navigation
- Fundo tab-bg translúcido, **borda superior 1dp line**, padding 8/10/20dp.
- Item: coluna, gap 3dp, ícone 22dp + label 11sp/700; não selecionado = muted,
  **selecionado = primary** (ícone e label).
- Sem pill de destaque do M3 — apenas cor.

## Sessões (Hoje)
- Cabeçalho da sessão: margem `16px 2px 10px`, título Manrope 800 + contador muted à direita.
- Cards de hábito dentro da sessão seguem `.habit-card`.

## Sombras
- Evitar sombras pesadas do M3. Cards usam borda 1dp, não elevação.
- Sombra do app frame (não usar nas telas): `0 24px 60px rgba(10,30,18,.28)`.

## O que NÃO fazer
- Não usar cores "seed" genéricas do MaterialTheme.
- Não usar `Card`/`Button`/`NavigationBar` com estilo padrão — sempre aplicar
  os tokens acima explicitamente.
- Não inventar textos: copiar os textos do protótipo literalmente.
