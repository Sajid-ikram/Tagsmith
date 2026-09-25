package com.tagsmith.core.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewLinkTest {

    private val placeId = "ChIJN1t_tDeuEmsRUsoyG83frY4"

    @Test
    fun `a bare Place ID becomes the review form URL`() {
        assertEquals(
            ReviewLink.Result.Ready(
                "https://search.google.com/local/writereview?placeid=$placeId",
                placeId,
            ),
            ReviewLink.parse("  $placeId  "),
        )
    }

    @Test
    fun `pulls the Place ID out of any URL that carries one`() {
        val result = ReviewLink.parse("https://www.google.com/maps/place/?q=place_id&placeid=$placeId&hl=en")
        assertEquals(placeId, (result as ReviewLink.Result.Ready).placeId)
    }

    @Test
    fun `passes a Business Profile review link straight through`() {
        val result = ReviewLink.parse("g.page/r/CX8kQp2mLd9AEBM/review")
        assertEquals(ReviewLink.Result.Ready("https://g.page/r/CX8kQp2mLd9AEBM/review", null), result)
    }

    @Test
    fun `upgrades an http review link to https`() {
        val result = ReviewLink.parse("http://search.google.com/local/writereview?placeid=$placeId")
        assertTrue((result as ReviewLink.Result.Ready).url.startsWith("https://"))
    }

    @Test
    fun `a link to the place itself is not a review link`() {
        assertTrue(ReviewLink.parse("https://maps.app.goo.gl/xyz123") is ReviewLink.Result.Problem)
        assertTrue(ReviewLink.parse("https://g.page/oakwell-coffee") is ReviewLink.Result.Problem)
    }

    @Test
    fun `short or odd text is not a Place ID`() {
        assertTrue(ReviewLink.parse("oakwell coffee bristol") is ReviewLink.Result.Problem)
        assertTrue(ReviewLink.parse("ChIJ123") is ReviewLink.Result.Problem)
    }

    @Test
    fun `nothing typed is not an error`() {
        assertEquals(ReviewLink.Result.Empty, ReviewLink.parse("   "))
    }
}
