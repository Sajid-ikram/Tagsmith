package com.tagsmith.core.data

import androidx.room.withTransaction
import com.tagsmith.core.nfc.NdefPayload
import com.tagsmith.core.nfc.PayloadCodec
import com.tagsmith.core.nfc.preview
import kotlinx.coroutines.flow.Flow

/** Saved payloads, in the order the operator put them. */
class TemplateRepository(private val db: TagsmithDatabase) {

    private val templates = db.templates()

    fun all(): Flow<List<Template>> = templates.observeAll()
    fun quick(limit: Int = 8): Flow<List<Template>> = templates.observeQuick(limit)
    fun template(id: Long): Flow<Template?> = templates.observe(id)

    suspend fun find(id: Long): Template? = templates.find(id)

    /** Creates a new template at the end of the list, or updates [id] in place. */
    suspend fun save(
        id: Long,
        name: String,
        payload: NdefPayload,
        clientId: Long?,
        favourite: Boolean,
    ): Long {
        val now = System.currentTimeMillis()
        val existing = if (id != 0L) templates.find(id) else null
        return if (existing == null) {
            templates.insert(
                Template(
                    name = name.trim(),
                    payloadType = payload.type,
                    payloadJson = PayloadCodec.encode(payload),
                    preview = payload.preview(),
                    clientId = clientId,
                    favourite = favourite,
                    sortOrder = templates.maxSortOrder() + 1,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } else {
            templates.update(
                existing.copy(
                    name = name.trim(),
                    payloadType = payload.type,
                    payloadJson = PayloadCodec.encode(payload),
                    preview = payload.preview(),
                    clientId = clientId,
                    favourite = favourite,
                    updatedAt = now,
                )
            )
            existing.id
        }
    }

    suspend fun duplicate(template: Template): Long {
        val now = System.currentTimeMillis()
        return templates.insert(
            template.copy(
                id = 0,
                name = "${template.name} (copy)",
                useCount = 0,
                favourite = false,
                sortOrder = templates.maxSortOrder() + 1,
                createdAt = now,
                updatedAt = now,
                lastUsedAt = null,
            )
        )
    }

    suspend fun delete(template: Template) = templates.delete(template)

    suspend fun setFavourite(id: Long, favourite: Boolean) = templates.setFavourite(id, favourite)

    /** Swaps a template with its neighbour. [by] is -1 for up, +1 for down. */
    suspend fun move(id: Long, by: Int) = db.withTransaction {
        val ordered = templates.all().toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in ordered.indices) return@withTransaction
        ordered.add(to, ordered.removeAt(from))
        // Renumber the whole list so gaps and ties from older edits disappear.
        templates.updateAll(ordered.mapIndexed { index, t -> t.copy(sortOrder = index) })
    }
}

fun Template.payload(): NdefPayload? = PayloadCodec.decode(payloadJson)
