package app.constancia.export

/**
 * Compartilha um arquivo de texto (JSON/CSV da exportação) usando o
 * compartilhamento nativo de cada plataforma. Implementado em
 * androidMain (Intent + FileProvider) e iosMain (UIActivityViewController).
 */
expect suspend fun shareTextFile(fileName: String, content: String, mimeType: String)
