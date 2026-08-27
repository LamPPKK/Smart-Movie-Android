package com.lamndt.smartmovie

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.designsystem.CinemaCardShape
import com.lamndt.smartmovie.designsystem.CinemaColors
import com.lamndt.smartmovie.designsystem.LoadingMessage
import com.lamndt.smartmovie.designsystem.PosterCard
import com.lamndt.smartmovie.designsystem.R
import com.lamndt.smartmovie.designsystem.RemoteArtwork
import com.lamndt.smartmovie.designsystem.SectionTitle
import com.lamndt.smartmovie.designsystem.StateMessage
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.CreditDetail
import com.lamndt.smartmovie.model.ImageKind
import com.lamndt.smartmovie.model.PersonSummary
import com.lamndt.smartmovie.model.TitleSummary
import kotlinx.coroutines.CancellationException

private sealed interface CreditDetailState {
    data object Loading : CreditDetailState
    data class Content(val value: CreditDetail) : CreditDetailState
    data class Failed(val message: String) : CreditDetailState
}

@Composable
internal fun CreditDetailScreen(
    creditId: String,
    label: String,
    catalog: CatalogV2Repository,
    images: ImageUrlFactory,
    language: String,
    includeAdult: Boolean,
    onBack: () -> Unit,
    onPerson: (PersonSummary) -> Unit,
    onTitle: (TitleSummary) -> Unit,
) {
    var state by remember(creditId, includeAdult) { mutableStateOf<CreditDetailState>(CreditDetailState.Loading) }
    var reloadKey by remember(creditId) { mutableStateOf(0) }
    LaunchedEffect(creditId, language, includeAdult, reloadKey) {
        state = CreditDetailState.Loading
        state = try {
            val detail = catalog.credit(creditId, language, includeAdult)
            CreditDetailState.Content(
                if (!includeAdult && detail.titleSummary?.adult == true) detail.copy(titleSummary = null) else detail,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            CreditDetailState.Failed(failure.message.orEmpty())
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(label.ifBlank { stringResource(R.string.credit_details) }, style = MaterialTheme.typography.headlineLarge)
        }
        when (val result = state) {
            CreditDetailState.Loading -> LoadingMessage(Modifier.fillMaxSize())
            is CreditDetailState.Failed -> StateMessage(
                stringResource(R.string.credit_unavailable),
                Modifier.fillMaxSize(),
                result.message,
                retry = { reloadKey += 1 },
            )
            is CreditDetailState.Content -> CreditContent(result.value, images, onPerson, onTitle)
        }
    }
}

@Composable
private fun CreditContent(
    detail: CreditDetail,
    images: ImageUrlFactory,
    onPerson: (PersonSummary) -> Unit,
    onTitle: (TitleSummary) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        detail.personSummary?.let { person ->
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { onPerson(person) },
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    RemoteArtwork(
                        images.url(person.profilePath, ImageKind.PROFILE),
                        person.name,
                        Modifier.width(140.dp).aspectRatio(.76f).clip(CinemaCardShape),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.person))
                        Text(person.name, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                        person.knownForDepartment?.let { Text(it, color = CinemaColors.Accent) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.role))
                detail.character?.let { CreditField(stringResource(R.string.character), it) }
                detail.job?.let { CreditField(stringResource(R.string.job), it) }
                detail.department?.let { CreditField(stringResource(R.string.department), it) }
            }
        }
        detail.titleSummary?.let { title ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(R.string.credit_title))
                    PosterCard(title, images.url(title.posterPath, ImageKind.POSTER), { onTitle(title) })
                }
            }
        }
    }
}

@Composable
private fun CreditField(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", fontWeight = FontWeight.Bold)
        Text(value, color = CinemaColors.Muted)
    }
}
