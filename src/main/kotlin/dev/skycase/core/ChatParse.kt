package dev.skycase.core

/** Pure text parsing for the item name embedded in a chat line. No MC deps -> unit-testable.
 * Pet lines are matched FIRST ("PET DROP! <name> (...)") so a pet name never falls through to the
 * generic "(...)" capture, which would otherwise grab the trailing "(+100% Magic Find)" parenthetical
 * and leave the caller with no name match on the pet itself. */
object ChatParse {
    private val petName = Regex("PET DROP! (.+?) \\(")
    private val genericParen = Regex("\\(([^)]+)\\)")
    private val caughtSuffix = Regex("You caught an? (.+?) (?:BRONZE|SILVER|GOLD|DIAMOND)!")
    private val countPrefix = Regex("^\\d+x ")

    fun itemNamedIn(text: String): String? {
        val raw = petName.find(text)?.groupValues?.get(1)
            ?: genericParen.find(text)?.groupValues?.get(1)
            ?: caughtSuffix.find(text)?.groupValues?.get(1)
            ?: return null
        return countPrefix.replace(raw.trim(), "").trim()
    }
}
