package com.lamndt.smartmovie

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class BackupRulesTest {
    @Test
    fun backupRulesIncludeOnlyRoomLibrary() {
        val legacyRules = File("src/main/res/xml/backup_rules.xml").readText()
        val modernRules = File("src/main/res/xml/data_extraction_rules.xml").readText()

        listOf(legacyRules, modernRules).forEach { rules ->
            assertThat(rules).contains("domain=\"database\" path=\"smartmovie_library.db\"")
            assertThat(rules).doesNotContain("sharedpref")
            assertThat(rules).doesNotContain("installation")
            assertThat(rules).doesNotContain("cache")
        }
    }
}
