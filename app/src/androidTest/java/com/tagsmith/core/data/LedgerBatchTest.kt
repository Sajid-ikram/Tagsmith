package com.tagsmith.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tagsmith.core.nfc.ChipType
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.NfcFailure
import com.tagsmith.core.nfc.OperationResult
import com.tagsmith.core.nfc.TagOperation
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.core.nfc.WriteContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What happens when a card lands, without needing a card: the ledger is fed
 * the same OperationResult the radio would produce. This is the part of batch
 * mode that has to be exactly right — a card counted twice, or not at all, is a
 * box of cards that doesn't match its paperwork.
 */
@RunWith(AndroidJUnit4::class)
class LedgerBatchTest {

    private lateinit var db: TagsmithDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var batches: BatchRepository
    private lateinit var templates: TemplateRepository
    private lateinit var clients: ClientRepository

    private val review = NdefPayload.Url("https://g.page/r/CX8kQp2mLd9AEBM/review")

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, TagsmithDatabase::class.java).build()
        ledger = LedgerRepository(db)
        batches = BatchRepository(db)
        templates = TemplateRepository(db)
        clients = ClientRepository(context, db)
    }

    @After
    fun tearDown() = db.close()

    private fun card(n: Int, at: Long = 1_000L + n) = TagSnapshot(
        uid = "04:A2:9C:6B:${n.toString().padStart(2, '0')}:80:00",
        chip = ChipType.NTAG215,
        chipLabel = "NTAG215",
        technologies = listOf("NfcA", "Ndef"),
        capacityBytes = 504,
        usedBytes = 44,
        writable = true,
        locked = false,
        canMakeReadOnly = true,
        formatted = true,
        formattable = false,
        supported = true,
        records = emptyList(),
        rawHex = "",
        readAt = at,
    )

    private fun write(context: WriteContext) =
        TagOperation.Write(review, verify = true, lockAfter = false, context = context)

    @Test
    fun a_batch_counts_each_card_names_it_and_closes_when_full() = runBlocking {
        val oakwell = clients.save(Client(name = "Oakwell Coffee", color = 0xFF3F6B57.toInt(), createdAt = 0))
        val template = templates.save(0, "Oakwell review", review, oakwell, favourite = true)
        val batch = batches.start(review, oakwell, template, target = 3, autoVerify = true, autoLock = false)
        val context = WriteContext(clientId = oakwell, templateId = template, batchId = batch)

        for (n in 1..3) ledger.record(OperationResult(write(context), card(n), verified = true))

        val done = batches.find(batch)!!
        assertEquals(3, done.writtenCount)
        assertEquals(BatchStatus.COMPLETE, done.status)
        assertNotNull(done.completedAt)
        assertNull("the clock stops when the batch fills", done.resumedAt)

        for (n in 1..3) {
            val tag = ledger.known(card(n).uid)!!
            assertEquals("Oakwell · card 0$n", tag.nickname)
            assertEquals(oakwell, tag.clientId)
            assertEquals(batch, tag.batchId)
        }

        val writes = batches.writes(batch).first()
        assertEquals("row N is card N", (1..3).map { card(it).uid }, writes.map { it.uid })
        assertEquals("Oakwell Coffee", writes.first().clientName)
        assertEquals(3, templates.find(template)!!.useCount)
    }

    @Test
    fun a_card_after_the_batch_is_full_is_logged_but_not_counted() = runBlocking {
        val batch = batches.start(review, null, null, target = 1, autoVerify = false, autoLock = false)
        val context = WriteContext(batchId = batch)

        ledger.record(OperationResult(write(context), card(1), verified = null))
        ledger.record(OperationResult(write(context), card(2), verified = null))

        assertEquals(1, batches.find(batch)!!.writtenCount)
        assertNull("the overflow write belongs to no batch", ledger.known(card(2).uid)!!.batchId)
        assertEquals(1, batches.writes(batch).first().size)
    }

    @Test
    fun failures_count_as_retries_and_never_as_cards() = runBlocking {
        val batch = batches.start(review, null, null, target = 5, autoVerify = true, autoLock = false)
        val op = write(WriteContext(batchId = batch))

        ledger.recordFailure(op, NfcFailure.TagLost, card(1))
        ledger.recordFailure(op, NfcFailure.VerificationMismatch("a", "b"), card(1))

        val after = batches.find(batch)!!
        assertEquals(0, after.writtenCount)
        assertEquals(2, after.failedCount)
        assertEquals(BatchStatus.RUNNING, after.status)
    }

    @Test
    fun pausing_stops_the_clock_and_resuming_restarts_it() = runBlocking {
        val batch = batches.start(review, null, null, target = 5, autoVerify = true, autoLock = false)
        batches.pause(batch)
        val paused = batches.find(batch)!!
        assertEquals(BatchStatus.PAUSED, paused.status)
        assertNull(paused.resumedAt)
        val frozen = paused.elapsedMillis(now = Long.MAX_VALUE)
        assertEquals("a paused clock ignores the wall clock", paused.activeMillis, frozen)

        batches.resume(batch)
        assertEquals(BatchStatus.RUNNING, batches.find(batch)!!.status)
    }

    @Test
    fun delivering_deploys_written_cards_but_leaves_locked_ones_locked() = runBlocking {
        val batch = batches.start(review, null, null, target = 2, autoVerify = false, autoLock = false)
        val context = WriteContext(batchId = batch)
        ledger.record(OperationResult(write(context), card(1), verified = null))
        ledger.record(OperationResult(write(context), card(2).copy(locked = true, writable = false), verified = null))

        batches.deliver(batch)

        assertEquals(BatchStatus.DELIVERED, batches.find(batch)!!.status)
        assertEquals(TagStatus.DEPLOYED, ledger.known(card(1).uid)!!.status)
        assertEquals(TagStatus.LOCKED, ledger.known(card(2).uid)!!.status)
    }

    /**
     * The fixture's read-back is empty, as it is when the live re-read fails and
     * Android hands back its cache of the card from before the write.
     */
    @Test
    fun a_successful_write_is_written_even_if_the_read_back_looks_blank() = runBlocking {
        ledger.record(OperationResult(write(WriteContext()), card(1), verified = null))
        val tag = ledger.known(card(1).uid)!!
        assertEquals(TagStatus.WRITTEN, tag.status)
        assertEquals(review.url, tag.contentSummary)
    }

    @Test
    fun deleting_a_client_detaches_everything_but_history_keeps_the_name() = runBlocking {
        val id = clients.save(Client(name = "Fern & Bloom", color = 0xFF3F6B57.toInt(), createdAt = 0))
        ledger.record(OperationResult(write(WriteContext(clientId = id)), card(1), verified = true))

        clients.delete(clients.find(id)!!)

        assertNull(ledger.known(card(1).uid)!!.clientId)
        val entry = ledger.allActivity().first().single()
        assertNull(entry.clientId)
        assertEquals("Fern & Bloom", entry.clientName)
    }

    @Test
    fun an_overwrite_without_a_client_keeps_the_one_the_tag_had() = runBlocking {
        val id = clients.save(Client(name = "Kiln & Co", color = 0xFF8A3324.toInt(), createdAt = 0))
        ledger.record(OperationResult(write(WriteContext(clientId = id)), card(1), verified = true))
        ledger.record(OperationResult(write(WriteContext()), card(1, at = 5_000), verified = true))

        assertEquals(id, ledger.known(card(1).uid)!!.clientId)
    }
}
