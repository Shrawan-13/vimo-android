package com.teamshryne.vimo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Split
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamshryne.vimo.EffectDescriptor
import com.teamshryne.vimo.model.ClipModel
import com.teamshryne.vimo.ui.theme.VimoColors

/**
 * The inspector: what is selected, and what can be done to it.
 *
 * The panel is deliberately a list of *cards* rather than a form: each section
 * is one thing you can change, and the section that matters is the one you
 * touched last.
 */
@Composable
fun Inspector(
    clip: ClipModel?,
    effects: List<EffectDescriptor>,
    onOpacityChange: (Float) -> Unit,
    onToggleEnabled: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onAddEffect: (EffectDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (clip == null) {
        EmptyInspector(modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Clip") {
                LabeledSlider(
                    label = "Opacity",
                    value = clip.opacity,
                    range = 0f..1f,
                    onValueChange = onOpacityChange,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onSplit) {
                        Icon(Icons.Filled.Split, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Split")
                    }
                    OutlinedButton(onClick = onToggleEnabled) {
                        Text(if (clip.enabled) "Disable" else "Enable")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete the clip",
                            tint = VimoColors.Error,
                        )
                    }
                }
                InfoRow("Speed", "%.2fx".format(clip.speed))
                InfoRow("Blend", clip.blend.replaceFirstChar { it.uppercase() })
                InfoRow("Effects", clip.effectCount.toString())
            }
        }

        item {
            var showEffects by remember { mutableStateOf(false) }
            SectionCard(title = "Effects") {
                if (clip.effectCount == 0) {
                    Text(
                        "No effects yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VimoColors.OnSurfaceMuted,
                    )
                } else {
                    Text(
                        "${clip.effectCount} effect(s) applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VimoColors.OnSurfaceMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (showEffects) {
                    effects.forEach { descriptor ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAddEffect(descriptor) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                descriptor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = VimoColors.OnSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                descriptor.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = VimoColors.OnSurfaceMuted,
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { showEffects = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VimoColors.SurfaceActive,
                            contentColor = VimoColors.OnSurface,
                        ),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add an effect")
                    }
                }
            }
        }
    }
}

/** The inspector's empty state. */
@Composable
private fun EmptyInspector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Nothing selected",
                style = MaterialTheme.typography.titleMedium,
                color = VimoColors.OnSurfaceMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap a clip on the timeline to edit it.",
                style = MaterialTheme.typography.bodySmall,
                color = VimoColors.OnSurfaceDisabled,
            )
        }
    }
}

/** A titled card that groups one set of controls. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VimoColors.SurfaceRaised)
            .padding(14.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = VimoColors.OnSurfaceMuted,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** A labelled slider with its value on the right. */
@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String = "%.2f".format(value),
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = VimoColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = VimoColors.OnSurfaceMuted,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = VimoColors.Accent,
                activeTrackColor = VimoColors.Accent,
                inactiveTrackColor = VimoColors.SurfaceActive,
            ),
        )
    }
}

/** A read-only row of information. */
@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = VimoColors.OnSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = VimoColors.OnSurface,
        )
    }
}
