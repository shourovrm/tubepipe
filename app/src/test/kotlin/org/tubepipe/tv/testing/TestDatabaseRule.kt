package org.tubepipe.tv.testing

import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.tubepipe.core.database.TubepipeDatabase

/** A fresh in-memory database per test (Robolectric), closed afterwards. */
class TestDatabaseRule : ExternalResource() {
    lateinit var database: TubepipeDatabase
        private set

    override fun before() {
        database = TubepipeDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    override fun after() {
        database.close()
    }
}
