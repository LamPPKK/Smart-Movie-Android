package com.lamndt.smartmovie.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.android.tools.screenshot.PreviewTest
import com.lamndt.smartmovie.remote.WatchTitleContext

@PreviewTest
@Preview(
    name = "wear_round_remote",
    device = "spec:width=227dp,height=227dp,dpi=320,isRound=true,chinSize=0dp",
    locale = "en",
)
@Composable
fun WearRemoteGolden() = WearCinemaTheme {
    WearRemoteScreen(
        state = WearRemoteUiState(
            context = WatchTitleContext(
                libraryKey = "movie:550",
                title = "Fight Club",
                mediaType = "movie",
                year = "1999",
                rating = 8.4,
                trailerAvailable = true,
                favorite = true,
            ),
            phoneConnected = true,
            phoneActive = true,
        ),
        onCommand = {},
        timeText = { TimeText(timeSource = FixedTimeSource) },
    )
}

private object FixedTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
