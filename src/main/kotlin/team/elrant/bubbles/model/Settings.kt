package team.elrant.bubbles.model

/**
 * User preferences, persisted by `SettingsStore`. The defaults are the app's original behaviour.
 *
 * @property flavor Catppuccin flavor of the theme.
 * @property accent Catppuccin colour used as the theme's primary colour.
 * @property enterSends `true`: Enter sends and Shift+Enter inserts a newline; `false`: Ctrl+Enter sends and
 *   Enter inserts a newline.
 * @property runGapMinutes a new "X says:" run starts after this many minutes of silence.
 * @property use24HourClock show message times as `14:05` rather than `2:05 PM`.
 * @property openOnMessage open a conversation window when a message arrives for a closed one; otherwise the
 *   contact is only marked as unread.
 * @property raiseOnMessage bring the conversation window to the front when a message arrives.
 * @property requestAttention ask the desktop to flag the conversation window when a message arrives.
 * @property encryptMessages encrypt outgoing messages with OMEMO whenever the contact supports it; otherwise they
 *   are sent as plain text.
 * @property sendReadReceipts tell contacts (chat markers, XEP-0333) when their messages are on screen in a focused
 *   conversation window.
 * @property requireDeviceApproval a contact's OMEMO device seen for the first time is not trusted until approved,
 *   instead of being trusted on first use.
 * @property groupOrder order of roster groups in the contact list; groups not listed follow, by name.
 */
data class Settings(
    val flavor: Flavor = Flavor.Mocha,
    val accent: Accent = Accent.Mauve,
    val enterSends: Boolean = true,
    val runGapMinutes: Int = DEFAULT_RUN_GAP_MINUTES,
    val use24HourClock: Boolean = true,
    val openOnMessage: Boolean = true,
    val raiseOnMessage: Boolean = false,
    val requestAttention: Boolean = true,
    val encryptMessages: Boolean = true,
    val sendReadReceipts: Boolean = true,
    val requireDeviceApproval: Boolean = false,
    val groupOrder: List<String> = emptyList(),
) {
    companion object {
        /** Default for [runGapMinutes]. */
        const val DEFAULT_RUN_GAP_MINUTES = 5

        /** Allowed values of [runGapMinutes]. */
        val RUN_GAP_RANGE = 1..60
    }
}

/**
 * Catppuccin flavor (https://catppuccin.com/palette).
 *
 * @property label user-facing name.
 * @property dark whether it is a dark theme.
 */
enum class Flavor(val label: String, val dark: Boolean) {
    Latte("Latte", dark = false),
    Frappe("Frappé", dark = true),
    Macchiato("Macchiato", dark = true),
    Mocha("Mocha", dark = true),
}

/**
 * Catppuccin accent colour; the actual colour depends on the [Flavor].
 *
 * @property label user-facing name.
 */
enum class Accent(val label: String) {
    Rosewater("Rosewater"),
    Flamingo("Flamingo"),
    Pink("Pink"),
    Mauve("Mauve"),
    Red("Red"),
    Maroon("Maroon"),
    Peach("Peach"),
    Yellow("Yellow"),
    Green("Green"),
    Teal("Teal"),
    Sky("Sky"),
    Sapphire("Sapphire"),
    Blue("Blue"),
    Lavender("Lavender"),
}
