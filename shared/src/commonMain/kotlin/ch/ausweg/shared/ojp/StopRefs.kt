package ch.ausweg.shared.ojp

/** Swiss DIDOK station numbers are the country code 85 followed by five digits. */
private const val SWISS_DIDOK_BASE = 8_500_000

private val DIDOK = Regex("""85\d{5}""")

/** `ch:1:sloid:<number>` optionally followed by `:<area>:<platform>`. */
private val SLOID = Regex("""ch:1:sloid:(\d{1,5})(:.*)?""")

/**
 * The DIDOK number of the station an OJP stop ref belongs to, or null if it isn't a Swiss stop ref.
 * `8501120` → 8501120; `ch:1:sloid:7000` and `ch:1:sloid:7000:4:7` → 8507000.
 */
fun didokOf(ref: String): Int? {
    val trimmed = ref.trim()
    val sloidNumber = SLOID.matchEntire(trimmed)?.groupValues?.get(1)
    return when {
        DIDOK.matches(trimmed) -> trimmed.toInt()
        sloidNumber != null -> SWISS_DIDOK_BASE + sloidNumber.toInt()
        else -> null
    }
}
