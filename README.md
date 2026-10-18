# Constância — Habit Tracker (Kotlin Multiplatform)

Rastreador de hábitos multiplataforma (Android + iOS, um código só em Kotlin +
Compose Multiplatform). **Nome "Constância" é provisório** (título do protótipo).

## Status

v1 em construção local. Offline-first: o banco local (SQLDelight) é a fonte de
verdade. A sincronização na nuvem (Supabase) está DEFERIDA — o código está
pronto para plugar (ver `SUPABASE_SETUP.md`).

## Estrutura

- `shared/` — todo o app (modelos, banco, repositórios, telas em Compose).
  - `commonMain` — UI + domínio + contratos.
  - `androidMain` / `iosMain` — drivers SQLite, config Supabase, share nativo.
- `androidApp/` — app Android (minSdk 24).
- `iosApp/` — projeto Xcode (validação do framework via CI).

## Build local

Precisa de JDK 17:

```bash
export JAVA_HOME=~/workspace/tools/jdk/jdk-17.0.20.1+1
./gradlew :androidApp:assembleDebug
```

O APK sai em `androidApp/build/outputs/apk/debug/`.

## CI

`.github/workflows/build.yml` — 2 jobs: APK debug (Linux) + framework iOS
simulador (macOS). iOS em aparelho real exige conta Apple Developer.

## Regras duras (não quebrar)

- minSdk 24: **NUNCA** `java.time` — usar `kotlinx-datetime`.
- Sem `String.format` em commonMain.
- Ícones: sempre `Icons.Filled.Nome` (material-icons-extended).
- Sem `/*` dentro de comentário/KDoc (comentários de bloco aninham).
- Cores via `colorScheme`, nunca hex inventado fora do tema.
- Ordem de Modifiers: layout → clip → background → clickable → padding.
