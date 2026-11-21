package ch.ausweg.shared.ojp

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.time.Instant

/** The OJP response could not be read (not XML, or a required element is missing). */
class OjpParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * A minimal read-only element tree. Elements and attributes are keyed by **local name**, so `siri:` / `ojp:`
 * prefixes don't matter. OJP responses are small, so reading them fully is fine.
 */
internal class XmlNode(
    val name: String,
    val attributes: Map<String, String>,
) {
    private val kids = mutableListOf<XmlNode>()
    private val textBuffer = StringBuilder()

    val children: List<XmlNode> get() = kids

    /** Text directly inside this element, trimmed. */
    val text: String get() = textBuffer.toString().trim()

    fun child(name: String): XmlNode? = kids.firstOrNull { it.name == name }

    fun children(name: String): List<XmlNode> = kids.filter { it.name == name }

    fun at(vararg path: String): XmlNode? = path.fold<String, XmlNode?>(this) { node, step -> node?.child(step) }

    /** Text at [path], or null when the element is missing or empty. */
    fun textAt(vararg path: String): String? = at(*path)?.text?.takeIf { it.isNotEmpty() }

    fun descendants(): Sequence<XmlNode> =
        sequence {
            for (kid in kids) {
                yield(kid)
                yieldAll(kid.descendants())
            }
        }

    fun descendants(name: String): Sequence<XmlNode> = descendants().filter { it.name == name }

    companion object {
        fun parse(xml: String): XmlNode {
            val stack = ArrayDeque<XmlNode>()
            val roots = mutableListOf<XmlNode>()
            try {
                val reader = xmlStreaming.newGenericReader(xml)
                while (reader.hasNext()) onEvent(reader, reader.next(), stack, roots)
            } catch (e: XmlException) {
                throw OjpParseException("Malformed XML: ${e.message}", e)
            }
            val problem =
                when {
                    stack.isNotEmpty() -> "unclosed <${stack.last().name}>"
                    roots.isEmpty() -> "no root element"
                    else -> null
                }
            if (problem != null) throw OjpParseException("Malformed XML: $problem")
            return roots.first()
        }

        private fun onEvent(
            reader: XmlReader,
            event: EventType,
            stack: ArrayDeque<XmlNode>,
            roots: MutableList<XmlNode>,
        ) {
            when (event) {
                EventType.START_ELEMENT -> {
                    val attributes =
                        (0 until reader.attributeCount).associate { i ->
                            reader.getAttributeLocalName(i) to reader.getAttributeValue(i)
                        }
                    val node = XmlNode(reader.localName, attributes)
                    stack.lastOrNull()?.kids?.add(node) ?: roots.add(node)
                    stack.addLast(node)
                }
                EventType.END_ELEMENT -> stack.removeLast()
                EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF ->
                    stack.lastOrNull()?.textBuffer?.append(
                        reader.text,
                    )
                else -> Unit
            }
        }
    }
}

internal fun XmlNode.required(vararg path: String): String =
    textAt(*path) ?: throw OjpParseException("<$name> without <${path.joinToString("/")}>")

internal fun XmlNode.requiredInstant(vararg path: String): Instant = parseInstant(required(*path))

internal fun XmlNode.instantAt(vararg path: String): Instant? = textAt(*path)?.let(::parseInstant)

/** OJP booleans default to false when absent. */
internal fun XmlNode.flag(name: String): Boolean = textAt(name)?.lowercase() == "true"

private fun parseInstant(value: String): Instant =
    try {
        Instant.parse(value)
    } catch (e: IllegalArgumentException) {
        throw OjpParseException("Bad timestamp '$value'", e)
    }
