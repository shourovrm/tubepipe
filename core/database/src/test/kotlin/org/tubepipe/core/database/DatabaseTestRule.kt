package org.tubepipe.core.database

import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/** One fresh in-memory database per test, closed afterwards so nothing leaks into the next test. */
class DatabaseTestRule : ExternalResource() {
    lateinit var database: TubepipeDatabase
        private set

    override fun before() {
        database = TubepipeDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    override fun after() {
        database.close()
    }
}
