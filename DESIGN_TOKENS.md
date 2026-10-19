# Constância — Design Tokens (fonte: protótipo HTML aprovado)

Extraído de `~/workspace/your_files/prot-tipo-habit-tracker/prot-tipo-habit-tracker.html`
e `~/workspace/your_files/preview-relat-rios/preview-relat-rios.html`.
REGRA: aplicar literalmente. Nada de "parecido".

## Paleta — app (light / dark)

| token | light | dark | uso |
|---|---|---|---|
| bg | #F5F3EC | #0D1511 | fundo das telas |
| ink | #152419 | #ECF3ED | texto principal |
| muted | #66766A | #93A69A | texto secundário |
| line | #E6E1D2 | #26392D | bordas 1dp dos cards |
| primary | #0B7A55 | #2FBF8F | botões, seleção, destaques |
| primary-soft | #E2F2E9 | #143327 | fundo suave do primário |
| surface | #FFFFFF | #17231C | cards |
| surface-2 | #EFEADF | #1F2F25 | superfícies secundárias |
| flame | #E86A17 | #FF8A3D | anel de progresso, streaks |
| on-primary | #FFFFFF | #07130D | texto sobre o primário |
| tab-bg | #FFFFFF 92% | #131E17 94% | fundo da bottom nav (translúcido) |

## Paleta — relatórios (extras do preview)

| token | light | dark |
|---|---|---|
| brand-strong | #5ED3AB | #095C40 |
| heat-0 | #E4EAE5 | #1C2A22 |
| amber / amber-soft | #F0A840 / #FBEBD3 | #D97706 / #3A2A10 |
| red / red-soft | #F08A5D / #FBE7DC | #C2410C / #3A2115 |
| ink-2 | #A9BCB1 | #43564D |
| ink-3 | #7A8B82 | #6E8277 |
| page-bg | #E9ECE8 | #0B100E |
| page-card | #FFFFFF | #16211B |
| page-line | #E1E7E2 | #223129 |

## Cartão de progresso (destaque do Hoje)
- Fundo = **ink** (INVERTIDO: card escuro no modo claro, card claro no modo escuro)
- Conteúdo = **bg**. Raio 24dp, padding 18dp, gap interno 16dp, margin-bottom 14dp.
- Anel: 84dp, track com 16% de opacidade, frente na cor **flame**, stroke 9dp.

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
