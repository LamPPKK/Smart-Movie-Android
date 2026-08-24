package com.lamndt.smartmovie.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class AccountMutationPayloadCompatibilityTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Test
    fun oldListItemMutationWithoutTitleSnapshotsStillDecodes() {
        val payload = json.decodeFromString<AccountMutationPayload>(
            """{"type":"mutate_list_items","listId":7,"items":[{"mediaType":"movie","mediaId":550}],"remove":false}""",
        ) as AccountMutationPayload.MutateListItems

        assertThat(payload.listId).isEqualTo(7)
        assertThat(payload.items.single().mediaId).isEqualTo(550)
        assertThat(payload.titles).isEmpty()
        assertThat(payload.remove).isFalse()
    }
}
