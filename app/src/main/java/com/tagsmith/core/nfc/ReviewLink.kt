package com.tagsmith.core.nfc

/**
 * Turns whatever the operator pastes into a Google review URL — the single most
 * common thing Tagsmith writes.
 *
 * Accepts a bare Place ID, any URL carrying `placeid=`, or a review link a
 * Business Profile already hands out (`g.page/r/…/review`). Searching Places by
 * name needs a Maps API key, so it is not done here.
 */
object ReviewLink {

    const val PLACE_ID_FINDER =
        "https://developers.google.com/maps/documentation/places/web-service/place-id"

    sealed interface Result {
        data object Empty : Result
        data class Ready(val url: String, val placeId: String?) : Result
        data class Problem(val message: String) : Result
    }

    fun forPlaceId(placeId: String): String =
        "https://search.google.com/local/writereview?placeid=$placeId"

    fun parse(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Empty

        // Any URL that names the place outright — Maps share links often do.
        Regex("[?&]placeid=([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.let { return Result.Ready(forPlaceId(it.groupValues[1]), it.groupValues[1]) }

        val lower = trimmed.lowercase()
        val isUrl = lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("g.page/") || lower.startsWith("www.")

        if (isUrl) {
            val url = if (lower.startsWith("http")) trimmed else "https://$trimmed"
            val isReviewLink = lower.contains("g.page/r/") && lower.trimEnd('/').endsWith("/review") ||
                lower.contains("search.google.com/local/writereview")
            return if (isReviewLink) {
                Result.Ready(url.replaceFirst("http://", "https://"), null)
            } else {
                Result.Problem(
                    "That's a link to the place, not to its review form. Paste the Place ID, " +
                        "or the \"Ask for reviews\" link from the Business Profile."
                )
            }
        }

        return if (PLACE_ID.matches(trimmed)) {
            Result.Ready(forPlaceId(trimmed), trimmed)
        } else {
            Result.Problem("That doesn't look like a Place ID. They're about 27 characters and usually start ChIJ.")
        }
    }

    /** Place IDs are URL-safe base64; real ones run to well over twenty characters. */
    private val PLACE_ID = Regex("[A-Za-z0-9_-]{20,}")
}
