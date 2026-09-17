package com.trae.expensetracker.ingest

import com.trae.expensetracker.ingest.parsers.ParseUtils

/**
 * Stable identity for a source message, used to remember that the user deleted it.
 *
 * Import is re-runnable because the phone inbox is the source of truth, so a deletion only
 * sticks if we can recognise the same message again on the next import.
 *
 * The signature is derived from the message body alone: bank and wallet messages always carry a
 * reference number or timestamp, so two genuinely distinct messages never share a body. Using the
 * body (rather than the sender) also means the signature can be computed when deleting a stored
 * transaction, which no longer carries its original sender.
 */
object ImportSignature {

    fun of(body: String): String =
        ParseUtils.sha256Hex(body.trim().replace(Regex("\\s+"), " ").lowercase())

    /** Short single-line preview for the Settings list. */
    fun previewOf(body: String): String =
        body.trim().replace(Regex("\\s+"), " ").take(80)
}
