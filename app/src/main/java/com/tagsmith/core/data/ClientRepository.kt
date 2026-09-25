package com.tagsmith.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Clients, and the logo files that go with them. */
class ClientRepository(
    private val context: Context,
    private val db: TagsmithDatabase,
) {
    private val clients = db.clients()

    fun summaries(): Flow<List<ClientSummary>> = clients.observeSummaries()
    fun all(): Flow<List<Client>> = clients.observeAll()
    fun client(id: Long): Flow<Client?> = clients.observe(id)
    fun tagsFor(id: Long): Flow<List<TagRecord>> = db.tags().observeByClient(id)
    fun batchesFor(id: Long): Flow<List<Batch>> = db.batches().observeByClient(id)
    fun templatesFor(id: Long): Flow<List<Template>> = db.templates().observeByClient(id)

    suspend fun find(id: Long): Client? = clients.find(id)

    suspend fun save(client: Client): Long =
        if (client.id == 0L) clients.insert(client) else client.id.also { clients.update(client) }

    suspend fun updateNotes(id: Long, notes: String) {
        clients.find(id)?.let { clients.update(it.copy(notes = notes)) }
    }

    /**
     * Deleting a client keeps everything that happened: tags, templates and
     * batches simply stop pointing at them, and history keeps the name it logged.
     */
    suspend fun delete(client: Client) {
        db.withTransaction {
            db.tags().detachClient(client.id)
            db.templates().detachClient(client.id)
            db.batches().detachClient(client.id)
            db.history().detachClient(client.id)
            clients.delete(client)
        }
        client.logoPath?.let { runCatching { File(it).delete() } }
    }

    /**
     * Copies a picked image into app storage, shrunk to [LOGO_EDGE] pixels on its
     * longest side. Returns the new path, or null if the image could not be read.
     */
    suspend fun importLogo(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LOGO_EDGE) sample *= 2
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val scale = LOGO_EDGE.toFloat() / max(decoded.width, decoded.height)
            val logo = if (scale < 1f) {
                decoded.scale(
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                )
            } else {
                decoded
            }

            val dir = File(context.filesDir, "logos").apply { mkdirs() }
            val file = File(dir, "logo_${System.currentTimeMillis()}.png")
            file.outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }.getOrNull()
    }

    fun deleteLogoFile(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }

    companion object {
        const val LOGO_EDGE = 256
    }
}
