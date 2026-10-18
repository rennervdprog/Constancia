package app.constancia.sync

/**
 * URL e chave anônima do Supabase, por plataforma (androidMain/iosMain).
 *
 * Os valores REAIS nunca ficam no código — cada plataforma fornece os seus
 * via actual. Hoje são placeholders: o app roda 100% local (offline-first)
 * e a sincronização na nuvem fica como "em breve" até o Renner criar o
 * projeto Supabase e preencher os valores (ver SUPABASE_SETUP.md na raiz).
 */
expect val SUPABASE_URL: String

expect val SUPABASE_ANON_KEY: String

/** True quando há valores reais configurados (saiu do placeholder). */
fun isSupabaseConfigured(): Boolean =
    !SUPABASE_URL.contains("SUA-URL") && !SUPABASE_ANON_KEY.contains("SUA-ANON-KEY")
