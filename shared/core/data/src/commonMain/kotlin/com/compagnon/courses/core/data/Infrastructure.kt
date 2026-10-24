package com.compagnon.courses.core.data

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Creates the ids of local rows (lists, items, sessions, purchases). */
fun interface IdGenerator {
    fun newId(): String
}

/** UUIDv7 (time ordered), as required for client-created rows (`docs/data-model.md` §5.1). */
@OptIn(ExperimentalUuidApi::class)
object UuidV7Generator : IdGenerator {
    override fun newId(): String = Uuid.generateV7().toString()
}
