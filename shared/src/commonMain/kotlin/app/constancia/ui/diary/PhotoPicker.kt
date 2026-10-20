package app.constancia.ui.diary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Abre o seletor de foto do dia (photo picker do sistema no Android).
 * O retorno é um caminho local estável (a foto é copiada para o
 * armazenamento interno do app) ou null se o usuário cancelar.
 * No iOS fica stub por enquanto (retorna null), como as demais integrações.
 */
@Composable
expect fun rememberDiaryPhotoPicker(onPicked: (String?) -> Unit): () -> Unit

/** Mostra a foto do dia a partir do caminho local salvo no registro. */
@Composable
expect fun DiaryPhotoImage(photoUri: String, modifier: Modifier = Modifier)
