package app.constancia.data

import app.cash.sqldelight.db.SqlDriver
import app.constancia.db.ConstanciaDb

/** Fábrica do driver SQLite por plataforma (androidMain/iosMain). */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

private var dbInstance: ConstanciaDb? = null

/** Singleton do banco local — fonte de verdade (offline-first). */
fun appDatabase(factory: DatabaseDriverFactory): ConstanciaDb {
    val existing = dbInstance
    if (existing != null) return existing
    val created = ConstanciaDb(factory.createDriver())
    dbInstance = created
    return created
}
