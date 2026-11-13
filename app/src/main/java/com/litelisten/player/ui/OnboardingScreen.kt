package com.litelisten.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.litelisten.player.R
import com.litelisten.player.core.designsystem.theme.LocalSpacing

@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            Icons.Filled.LibraryMusic,
            stringResource(R.string.onboarding_1_title),
            stringResource(R.string.onboarding_1_desc),
        ),
        OnboardingPage(
            Icons.Filled.Lock,
            stringResource(R.string.onboarding_2_title),
            stringResource(R.string.onboarding_2_desc),
        ),
        OnboardingPage(
            Icons.Filled.Wifi,
            stringResource(R.string.onboarding_3_title),
            stringResource(R.string.onboarding_3_desc),
        ),
    )
    val current = pages[page]
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = current.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.size(spacing.xxxl))
        Text(current.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(spacing.l))
        Text(
            current.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(spacing.xxxl))
        Button(
            onClick = { if (page < pages.lastIndex) page++ else onDone() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (page < pages.lastIndex) R.string.action_next else R.string.action_start))
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.action_skip)) }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
)
