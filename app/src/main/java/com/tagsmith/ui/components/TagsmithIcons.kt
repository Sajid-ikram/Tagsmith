package com.tagsmith.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The line icons from the screen board, kept as stroked vectors so they stay
 * hairline-consistent with the 2px rules around them. [Icon] tints them.
 */
private fun stroked(name: String, path: String, width: Float = 2f): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

private fun filled(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()

object TagsmithIcons {

    /** The mark: three radiating arcs. The app's whole idea in one glyph. */
    val Nfc = stroked(
        "nfc",
        "M4 8a12 12 0 0 1 0 8M9 6a16 16 0 0 1 0 12M14 4a20 20 0 0 1 0 16",
    )

    val NfcOff = stroked(
        "nfcOff",
        "M4 8a12 12 0 0 1 0 8M9 6a16 16 0 0 1 0 12M14 4a20 20 0 0 1 0 16M3 21 21 3",
    )

    val Home = stroked("home", "M4 11 12 4l8 7v9H4z")

    val Tag = stroked(
        "tag",
        "M3 12V4h8l9 9-8 8-9-9zM8.9 7.5a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 1 1 2.8 0",
    )

    val Clients = stroked(
        "clients",
        "M12.4 8a3.4 3.4 0 1 1-6.8 0 3.4 3.4 0 1 1 6.8 0M3 20a6 6 0 0 1 12 0" +
            "M17 11a3 3 0 1 0 0-6M18 20a5.6 5.6 0 0 0-3-5",
    )

    val History = stroked("history", "M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0M12 7v5l3 2")

    val Write = stroked("write", "M12 19V5M5 12l7-7 7 7")
    val Read = stroked("read", "M12 5v14M5 12l7 7 7-7")

    val Lock = stroked("lock", "M4 10h16v10H4zM8 10V7a4 4 0 0 1 8 0v3")

    val Alert = stroked("alert", "M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0M12 8v5M12 16.3v.4")

    val Warning = stroked("warning", "M12 3 2 20h20zM12 9v5M12 16.8v.4")

    val Settings = stroked(
        "settings",
        "M15 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0" +
            "M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21" +
            "a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 7 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1" +
            "A1.6 1.6 0 0 0 3 15a2 2 0 0 1-2-2 2 2 0 0 1 2-2 1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1" +
            "a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9 4.6 2 2 0 0 1 11 3a2 2 0 0 1 2 2 1.6 1.6 0 0 0 1 1.5" +
            "a1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V11a2 2 0 0 1 0 4h-.1z",
        width = 1.8f,
    )

    val Close = stroked("close", "M18 6 6 18M6 6l12 12")
    val Back = stroked("back", "M19 12H5M12 19l-7-7 7-7")
    val ArrowRight = stroked("arrowRight", "M5 12h14M13 5l7 7-7 7")
    val ExternalLink = stroked("externalLink", "M7 17 17 7M9 7h8v8")
    val Copy = stroked("copy", "M9 9h11v11H9zM5 15V4h11")
    val Edit = stroked("edit", "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")

    val Link = stroked(
        "link",
        "M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1",
    )

    val Search = stroked("search", "M18 11a7 7 0 1 1-14 0 7 7 0 1 1 14 0M20 20l-4-4")
    val ChevronUp = stroked("chevronUp", "M18 15l-6-6-6 6")
    val ChevronDown = stroked("chevronDown", "M6 9l6 6 6-6")
    val Check = stroked("check", "M5 12.5l4.5 4.5L19 7", width = 2.4f)
    val Trash = stroked("trash", "M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13")
    val Download = stroked("download", "M12 3v12M7 10l5 5 5-5M4 21h16")
    val Plus = stroked("plus", "M12 5v14M5 12h14")
    val Format = stroked("format", "M3 12h18M12 3v18")
    val TextRecord = stroked("text", "M4 7V5h16v2M12 5v14M9 19h6")
    val Paste = stroked("paste", "M9 4h6v3H9zM7 5H5v15h14V5h-2")
    val Wifi = stroked("wifi", "M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.4v.4M2 9a15 15 0 0 1 20 0")

    val More = filled(
        "more",
        "M13.4 5a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 1 1 2.8 0" +
            "M13.4 12a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 1 1 2.8 0" +
            "M13.4 19a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 1 1 2.8 0",
    )

    val Star = filled("star", "m12 3 2.6 5.6 6.4.7-4.8 4.2 1.4 6.1L12 16.6 6.4 19.6l1.4-6.1L3 9.3l6.4-.7z")
}
