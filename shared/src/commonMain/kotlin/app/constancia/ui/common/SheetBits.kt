package app.constancia.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.theme.constanciaExtra

/** Rótulo de seção dentro dos sheets (formulários). */
@Composable
fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Segmentado (DESIGN_TOKENS.md): botões flex-1, padding 9/6dp, raio 10dp,
 * Manrope 13/700 (labelLarge) muted; selecionado fundo surface com sombra
 * leve "0 1px 4px rgba(0,0,0,.14)" e texto ink.
 */
@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tagPrefix: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            SegmentedChip(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                tag = if (tagPrefix != null) tagPrefix + "_" + index else null,
            )
        }
    }
}

@Composable
private fun RowScope.SegmentedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .weight(1f)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(10.dp),
                        spotColor = Color.Black.copy(alpha = 0.14f),
                        ambientColor = Color.Black.copy(alpha = 0.14f),
                    )
                } else {
                    Modifier
                },
            )
            .background(
                if (selected) scheme.surface else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 6.dp, vertical = 9.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}

/** Card `.tile` do protótipo: surface, borda 1dp line, raio 20dp, padding 15/16dp. */
@Composable
fun TileCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        content = content,
    )
}

/** Botão primário do protótipo (.btn-primary): largura total, raio 15dp,
 *  padding vertical 15dp, fundo primary, texto Manrope 15/800 e sombra
 *  roxa suave "0 14px 26px -10px rgba(75,29,150,.5)". */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(15.dp),
                spotColor = primary.copy(alpha = 0.5f),
                ambientColor = primary.copy(alpha = 0.5f),
            )
            .clip(RoundedCornerShape(15.dp))
            .background(primary)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Cabeçalho das telas filhas do protótipo (.backrow): botão de voltar
 * 40dp raio 13 (surface, borda line-soft, sombra leve), título Sora
 * 21/800 e subtítulo 12.5/700 muted logo abaixo.
 */
@Composable
fun BackRow(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(13.dp),
                    spotColor = Color(0xFF26104A).copy(alpha = 0.16f),
                    ambientColor = Color(0xFF26104A).copy(alpha = 0.16f),
                )
                .clip(RoundedCornerShape(13.dp))
                .background(scheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.constanciaExtra.lineSoft,
                    RoundedCornerShape(13.dp),
                )
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Voltar",
                tint = scheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Botão secundário: borda 1.5dp line, raio 12dp, fundo bg, texto ink 13.5/800. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
