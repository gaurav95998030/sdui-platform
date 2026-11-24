package com.sdui.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// ---------------------------------------------------------------------------
// Dimension
// ---------------------------------------------------------------------------

/**
 * A layout dimension expressed by the server. Accepted JSON forms:
 *
 * ```
 * "width": 120            -> 120.dp
 * "width": "120dp"        -> 120.dp
 * "width": "match_parent" -> fillMaxWidth()
 * "width": "wrap_content" -> wrapContentWidth()
 * "width": "50%"          -> fillMaxWidth(0.5f)
 * ```
 */
@Serializable(with = DimensionSerializer::class)
public sealed interface Dimension {
    public data object Fill : Dimension
    public data object Wrap : Dimension
    public data class Points(val dp: Float) : Dimension
    public data class Fraction(val value: Float) : Dimension

    public companion object {
        public fun parse(raw: String): Dimension? = when (raw.trim().lowercase()) {
            "match_parent", "match", "fill", "fill_max", "full", "100%" -> Fill
            "wrap_content", "wrap", "auto", "intrinsic" -> Wrap
            else -> {
                val text = raw.trim()
                when {
                    text.endsWith("%") ->
                        text.dropLast(1).toFloatOrNull()?.let { Fraction((it / 100f).coerceIn(0f, 1f)) }
                    text.endsWith("dp", ignoreCase = true) ->
                        text.dropLast(2).toFloatOrNull()?.let { Points(it) }
                    text.endsWith("px", ignoreCase = true) ->
                        text.dropLast(2).toFloatOrNull()?.let { Points(it) }
                    else -> text.toFloatOrNull()?.let { Points(it) }
                }
            }
        }
    }
}

public object DimensionSerializer : KSerializer<Dimension> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sdui.Dimension", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Dimension {
        val json = decoder as? JsonDecoder ?: return Dimension.Wrap
        val element = json.decodeJsonElement() as? JsonPrimitive ?: return Dimension.Wrap
        element.doubleOrNull?.let { return Dimension.Points(it.toFloat()) }
        return Dimension.parse(element.content) ?: Dimension.Wrap
    }

    override fun serialize(encoder: Encoder, value: Dimension) {
        val json = encoder as? JsonEncoder ?: return
        json.encodeJsonElement(
            when (value) {
                Dimension.Fill -> JsonPrimitive("match_parent")
                Dimension.Wrap -> JsonPrimitive("wrap_content")
                is Dimension.Points -> JsonPrimitive(value.dp)
                is Dimension.Fraction -> JsonPrimitive("${value.value * 100}%")
            },
        )
    }
}

// ---------------------------------------------------------------------------
// EdgeInsets (padding / margin)
// ---------------------------------------------------------------------------

/**
 * Accepted JSON forms: `16`, `"16"`, `{ "all": 16 }`,
 * `{ "horizontal": 16, "vertical": 8 }`, `{ "start": 4, "top": 8, "end": 4, "bottom": 8 }`.
 * `left`/`right` are accepted as aliases of `start`/`end`.
 */
@Serializable(with = EdgeInsetsSerializer::class)
public data class EdgeInsets(
    val start: Float = 0f,
    val top: Float = 0f,
    val end: Float = 0f,
    val bottom: Float = 0f,
) {
    public val isEmpty: Boolean get() = start == 0f && top == 0f && end == 0f && bottom == 0f

    public companion object {
        public val Zero: EdgeInsets = EdgeInsets()
        public fun all(value: Float): EdgeInsets = EdgeInsets(value, value, value, value)
    }
}

public object EdgeInsetsSerializer : KSerializer<EdgeInsets> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sdui.EdgeInsets", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): EdgeInsets {
        val json = decoder as? JsonDecoder ?: return EdgeInsets.Zero
        return when (val element = json.decodeJsonElement()) {
            is JsonPrimitive -> {
                val value = element.doubleOrNull?.toFloat() ?: element.content.toFloatOrNull() ?: 0f
                EdgeInsets.all(value)
            }

            is JsonObject -> {
                fun num(vararg keys: String): Float? = keys.firstNotNullOfOrNull { key ->
                    (element[key] as? JsonPrimitive)?.let { it.doubleOrNull?.toFloat() ?: it.content.toFloatOrNull() }
                }
                val all = num("all") ?: 0f
                val horizontal = num("horizontal", "h") ?: all
                val vertical = num("vertical", "v") ?: all
                EdgeInsets(
                    start = num("start", "left") ?: horizontal,
                    top = num("top") ?: vertical,
                    end = num("end", "right") ?: horizontal,
                    bottom = num("bottom") ?: vertical,
                )
            }

            else -> EdgeInsets.Zero
        }
    }

    override fun serialize(encoder: Encoder, value: EdgeInsets) {
        val json = encoder as? JsonEncoder ?: return
        json.encodeJsonElement(
            JsonObject(
                mapOf(
                    "start" to JsonPrimitive(value.start),
                    "top" to JsonPrimitive(value.top),
                    "end" to JsonPrimitive(value.end),
                    "bottom" to JsonPrimitive(value.bottom),
                ),
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Corner radius
// ---------------------------------------------------------------------------

/** Accepted JSON forms: `12`, `{ "topStart": 12, "topEnd": 12 }`. */
@Serializable(with = CornersSerializer::class)
public data class Corners(
    val topStart: Float = 0f,
    val topEnd: Float = 0f,
    val bottomEnd: Float = 0f,
    val bottomStart: Float = 0f,
) {
    public val isEmpty: Boolean
        get() = topStart == 0f && topEnd == 0f && bottomEnd == 0f && bottomStart == 0f

    public companion object {
        public fun all(value: Float): Corners = Corners(value, value, value, value)
    }
}

public object CornersSerializer : KSerializer<Corners> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sdui.Corners", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Corners {
        val json = decoder as? JsonDecoder ?: return Corners()
        return when (val element = json.decodeJsonElement()) {
            is JsonPrimitive ->
                Corners.all(element.doubleOrNull?.toFloat() ?: element.content.toFloatOrNull() ?: 0f)

            is JsonObject -> {
                fun num(vararg keys: String): Float? = keys.firstNotNullOfOrNull { key ->
                    (element[key] as? JsonPrimitive)?.let { it.doubleOrNull?.toFloat() ?: it.content.toFloatOrNull() }
                }
                val all = num("all") ?: 0f
                Corners(
                    topStart = num("topStart", "topLeft") ?: num("top") ?: all,
                    topEnd = num("topEnd", "topRight") ?: num("top") ?: all,
                    bottomEnd = num("bottomEnd", "bottomRight") ?: num("bottom") ?: all,
                    bottomStart = num("bottomStart", "bottomLeft") ?: num("bottom") ?: all,
                )
            }

            else -> Corners()
        }
    }

    override fun serialize(encoder: Encoder, value: Corners) {
        val json = encoder as? JsonEncoder ?: return
        json.encodeJsonElement(
            JsonObject(
                mapOf(
                    "topStart" to JsonPrimitive(value.topStart),
                    "topEnd" to JsonPrimitive(value.topEnd),
                    "bottomEnd" to JsonPrimitive(value.bottomEnd),
                    "bottomStart" to JsonPrimitive(value.bottomStart),
                ),
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Enumerations
//
// Every enum has a first entry that doubles as the coercion target for unknown
// values (the parser enables `coerceInputValues`), so a newer server vocabulary
// never crashes an older client.
// ---------------------------------------------------------------------------

@Serializable
public enum class AlignmentSpec {
    @SerialName("topStart") TOP_START,
    @SerialName("topCenter") TOP_CENTER,
    @SerialName("topEnd") TOP_END,
    @SerialName("centerStart") CENTER_START,
    @SerialName("center") CENTER,
    @SerialName("centerEnd") CENTER_END,
    @SerialName("bottomStart") BOTTOM_START,
    @SerialName("bottomCenter") BOTTOM_CENTER,
    @SerialName("bottomEnd") BOTTOM_END,
}

@Serializable
public enum class ArrangementSpec {
    @SerialName("start") START,
    @SerialName("center") CENTER,
    @SerialName("end") END,
    @SerialName("spaceBetween") SPACE_BETWEEN,
    @SerialName("spaceAround") SPACE_AROUND,
    @SerialName("spaceEvenly") SPACE_EVENLY,
}

@Serializable
public enum class CrossAxisAlignment {
    @SerialName("start") START,
    @SerialName("center") CENTER,
    @SerialName("end") END,
    @SerialName("stretch") STRETCH,
    @SerialName("baseline") BASELINE,
}

@Serializable
public enum class FontWeightSpec {
    @SerialName("normal") NORMAL,
    @SerialName("thin") THIN,
    @SerialName("light") LIGHT,
    @SerialName("medium") MEDIUM,
    @SerialName("semibold") SEMI_BOLD,
    @SerialName("bold") BOLD,
    @SerialName("extrabold") EXTRA_BOLD,
    @SerialName("black") BLACK,
}

@Serializable
public enum class TextAlignSpec {
    @SerialName("start") START,
    @SerialName("center") CENTER,
    @SerialName("end") END,
    @SerialName("justify") JUSTIFY,
}

@Serializable
public enum class TextOverflowSpec {
    @SerialName("clip") CLIP,
    @SerialName("ellipsis") ELLIPSIS,
    @SerialName("visible") VISIBLE,
}

@Serializable
public enum class TextDecorationSpec {
    @SerialName("none") NONE,
    @SerialName("underline") UNDERLINE,
    @SerialName("lineThrough") LINE_THROUGH,
}

@Serializable
public enum class ContentScaleSpec {
    @SerialName("fit") FIT,
    @SerialName("crop") CROP,
    @SerialName("fillBounds") FILL_BOUNDS,
    @SerialName("fillWidth") FILL_WIDTH,
    @SerialName("fillHeight") FILL_HEIGHT,
    @SerialName("inside") INSIDE,
    @SerialName("none") NONE,
}

@Serializable
public enum class HttpMethod {
    @SerialName("GET") GET,
    @SerialName("POST") POST,
    @SerialName("PUT") PUT,
    @SerialName("DELETE") DELETE,
    @SerialName("PATCH") PATCH,
    ;

    public val allowsBody: Boolean get() = this != GET && this != DELETE
}

@Serializable
public enum class NavigationMode {
    @SerialName("push") PUSH,
    @SerialName("replace") REPLACE,
    @SerialName("pop") POP,
    @SerialName("popToRoot") POP_TO_ROOT,
    @SerialName("popTo") POP_TO,
    @SerialName("modal") MODAL,
    @SerialName("bottomSheet") BOTTOM_SHEET,
}
