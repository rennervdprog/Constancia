package app.constancia.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// iOS: stub por enquanto (como as demais integrações pendentes da plataforma).
@Composable
actual fun rememberDiaryPhotoPicker(onPicked: (String?) -> Unit): () -> Unit =
    { onPicked(null) }

@Composable
actual fun DiaryPhotoImage(photoUri: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
