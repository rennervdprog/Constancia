package app.constancia.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private lateinit var appContext: Context

/** Chamado uma vez pelo MainActivity para liberar o compartilhamento. */
fun initShareContext(context: Context) {
    appContext = context.applicationContext
}

actual suspend fun shareTextFile(fileName: String, content: String, mimeType: String) {
    val file = File(appContext.cacheDir, fileName)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    appContext.startActivity(Intent.createChooser(intent, "Exportar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
