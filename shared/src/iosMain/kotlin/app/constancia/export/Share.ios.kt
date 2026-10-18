package app.constancia.export

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun shareTextFile(fileName: String, content: String, mimeType: String) {
    val tmp = platform.Foundation.NSTemporaryDirectory() + fileName
    (content as NSString).writeToFile(tmp, true, NSUTF8StringEncoding, null)
    val url = platform.Foundation.NSURL.fileURLWithPath(tmp)
    val controller = UIActivityViewController(listOf(url), null)
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    root?.presentViewController(controller, true, null)
}
