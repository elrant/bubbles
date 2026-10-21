package team.elrant.bubbles

/**
 * An expected sign-in failure, such as a malformed address, an unreachable server or rejected
 * credentials, as thrown by [Bubbles.signIn].
 *
 * @property message user-facing description of what went wrong.
 * @param cause the underlying exception, if any.
 */
class SignInException(override val message: String, cause: Throwable? = null) : Exception(message, cause)
