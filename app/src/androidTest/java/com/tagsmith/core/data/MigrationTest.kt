package com.tagsmith.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 shipped. Anyone who installed it has a ledger, and upgrading must never
 * cost them a row of it. This runs the real generated migration against a
 * database built from the exported v1 schema, then checks Room accepts the
 * result as v2.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val db = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TagsmithDatabase::class.java,
    )

    @Test
    fun v1_to_v2_keeps_every_tag_and_history_row() {
        helper.createDatabase(db, 1).apply {
            execSQL(
                "INSERT INTO tags (uid, nickname, chipLabel, technologies, capacityBytes, usedBytes, status, " +
                    "contentSummary, clientName, writable, firstSeenAt, lastSeenAt, lastWrittenAt, inInventory) " +
                    "VALUES ('04:A2:9C:6B:07:80:00', 'Oakwell · card 07', 'NTAG215', 'NfcA · Ndef', 504, 38, " +
                    "'WRITTEN', 'https://g.page/r/x/review', NULL, 1, 1000, 2000, 2000, 1)"
            )
            execSQL(
                "INSERT INTO history (id, action, uid, tagLabel, chipLabel, clientName, detail, payload, readBack, " +
                    "verified, success, errorMessage, timestamp) VALUES (7, 'WRITE', '04:A2:9C:6B:07:80:00', " +
                    "'Oakwell · card 07', 'NTAG215', 'Oakwell Coffee', 'URL · verified', 'https://g.page/r/x/review', " +
                    "NULL, 1, 1, NULL, 2000)"
            )
            close()
        }

        // Validates the migrated schema against the exported v2 schema, column for column.
        val migrated = helper.runMigrationsAndValidate(db, 2, true)

        migrated.query("SELECT nickname, status, clientId, batchId, inInventory FROM tags").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Oakwell · card 07", c.getString(0))
            assertEquals("WRITTEN", c.getString(1))
            assertTrue("no client yet", c.isNull(2))
            assertTrue("no batch yet", c.isNull(3))
            assertEquals(1, c.getInt(4))
        }

        // History keeps the client name it logged, and gains the link columns empty.
        migrated.query("SELECT id, clientName, clientId, batchId, verified FROM history").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(7L, c.getLong(0))
            assertEquals("Oakwell Coffee", c.getString(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
            assertEquals(1, c.getInt(4))
        }

        // The v1 free-text column nothing could set is gone from tags.
        migrated.query("SELECT name FROM pragma_table_info('tags')").use { c ->
            val columns = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
            assertFalse("clientName" in columns)
        }

        // And the new tables exist, empty.
        for (table in listOf("clients", "templates", "batches")) {
            migrated.query("SELECT COUNT(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(table, 0, c.getInt(0))
            }
        }
    }
}
