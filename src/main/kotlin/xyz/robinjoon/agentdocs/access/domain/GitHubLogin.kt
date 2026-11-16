package xyz.robinjoon.agentdocs.access.domain

// A GitHub login as someone typed or GitHub reported it. GitHub compares logins without case, so two logins name the
// same account when their keys match; the value keeps the spelling it was given for display. Construction keeps the
// lenient shape stored accounts and identifiers were read with; a new invitation checks isValidInvitation as well.
data class GitHubLogin(
    val value: String,
) {
    init {
        if (!isValid(value)) {
            throw InvalidAccessException("a GitHub login is 1 to 39 letters, digits, or hyphens starting with a letter or digit")
        }
    }

    val key: String get() = value.lowercase()

    companion object {
        private val pattern = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")

        // What GitHub lets someone sign up with today: letters and digits, single hyphens only between them, 1 to 39
        // long. Only new invitations hold to it, so a login stored before it never becomes unreadable.
        private val invitationPattern = Regex("[A-Za-z0-9]+(-[A-Za-z0-9]+)*")
        private const val MAX_LENGTH = 39

        fun isValid(value: String): Boolean = pattern.matches(value)

        fun isValidInvitation(value: String): Boolean = value.length <= MAX_LENGTH && invitationPattern.matches(value)
    }
}
