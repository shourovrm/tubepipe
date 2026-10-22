package org.tubepipe.tv.home

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tubepipe.tv.testing.videos

class FileRecommendationStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savedVideosAreReadBackUnchanged() = runTest {
        val file = temporaryFolder.root.resolve("recommended.json")
        FileRecommendationStore(file).save(videos("v", 5))

        assertEquals(videos("v", 5), FileRecommendationStore(file).load())
    }

    @Test
    fun missingFileMeansNothingSaved() = runTest {
        assertEquals(emptyList<Any>(), FileRecommendationStore(temporaryFolder.root.resolve("absent.json")).load())
    }

    @Test
    fun unreadableFileIsTreatedAsEmpty() = runTest {
        // It is only a start-up cache; a damaged file must not stop Home from loading.
        val file = temporaryFolder.root.resolve("recommended.json")
        file.writeText("{ not json")

        assertEquals(emptyList<Any>(), FileRecommendationStore(file).load())
    }
}
