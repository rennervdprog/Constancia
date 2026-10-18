# Supabase — sincronização na nuvem (DEFERIDO)

A v1 do app é 100% local (SQLDelight, offline-first). A sincronização na nuvem
entra quando o Renner criar o projeto Supabase do app (**projeto NOVO e
separado** — nunca o projeto compartilhado do ItaSuper).

## Quando a conta existir

1. Criar o projeto no Supabase e anotar a URL + `anon key` (nunca a
   `service_role`).
2. Preencher os `actual`:
   - Android: `shared/src/androidMain/kotlin/app/constancia/sync/SupabaseConfig.android.kt`
   - iOS: `shared/src/iosMain/kotlin/app/constancia/sync/SupabaseConfig.ios.kt`
   - Recomendado: gerar via `BuildConfig` (de `local.properties` + secrets do
     CI) em vez de hardcoded — a chave real **nunca** é commitada.
3. Criar as tabelas no banco (espelhar o schema de `Constancia.sq`):
   `habit`, `checkin`, `task_item`, `diary_entry`, `challenge` — com RLS por
   `auth.uid()`.
4. Implementar `SupabaseSyncRepository : SyncRepository` (upload/download +
   resolução simples "último vence") e trocar a instância em `AppDeps.kt`
   (`LocalSyncRepository` → `SupabaseSyncRepository`). Nenhuma tela muda:
   todas dependem só da interface `SyncRepository`.

## Estado atual no app

- `SyncRepository` + `LocalSyncRepository`: status "nuvem em breve".
- `SUPABASE_URL` / `SUPABASE_ANON_KEY`: placeholders; `isSupabaseConfigured()`
  retorna false até os valores reais entrarem.
- Tela Mais mostra "Sincronização na nuvem — em breve".
