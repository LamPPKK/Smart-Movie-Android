package com.lamndt.smartmovie.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.CinemaCardShape
import com.lamndt.smartmovie.designsystem.R

@Composable
fun AboutScreen(versionName: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.cinematic_description), color = CinemaColors.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Surface(shape = CinemaCardShape, color = CinemaColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.data_attribution), color = CinemaColors.Muted, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.version_format, versionName), color = CinemaColors.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
