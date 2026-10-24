package team.elrant.bubbles.secrets

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

// The parts of the freedesktop.org Secret Service API (https://specifications.freedesktop.org/secret-service-spec/)
// that Keyring uses. Method names are mapped with DBusMemberName because D-Bus names are PascalCase.

/** `org.freedesktop.Secret.Service`, at [SERVICE_PATH] on the bus name [BUS_NAME]. */
@DBusInterfaceName("org.freedesktop.Secret.Service")
@JvmSuppressWildcards
internal interface ServiceApi : DBusInterface {
  /** Opens a session with the given algorithm (`plain`: secrets cross the local bus unencrypted). */
  @DBusMemberName("OpenSession")
  fun openSession(algorithm: String, input: Variant<*>): OpenSessionResult

  /** Items whose attributes match all of [attributes]. */
  @DBusMemberName("SearchItems")
  fun searchItems(attributes: Map<String, String>): SearchResult

  /** Unlocks [objects]; unlocking may need a [PromptApi] the user answers. */
  @DBusMemberName("Unlock")
  fun unlock(objects: List<DBusPath>): UnlockResult

  /** The object an alias such as `default` refers to, or `/` if there is none. */
  @DBusMemberName("ReadAlias")
  fun readAlias(name: String): DBusPath
}

/** `org.freedesktop.Secret.Collection`. */
@DBusInterfaceName("org.freedesktop.Secret.Collection")
@JvmSuppressWildcards
internal interface CollectionApi : DBusInterface {
  /** Creates (or with [replace], replaces) an item holding [secret]. */
  @DBusMemberName("CreateItem")
  fun createItem(properties: Map<String, Variant<*>>, secret: Secret, replace: Boolean): CreateItemResult
}

/** `org.freedesktop.Secret.Item`. */
@DBusInterfaceName("org.freedesktop.Secret.Item")
@JvmSuppressWildcards
internal interface ItemApi : DBusInterface {
  /** The item's secret, as seen through [session]. */
  @DBusMemberName("GetSecret")
  fun getSecret(session: DBusPath): Secret

  /** Deletes the item; returns a prompt path, or `/`. */
  @DBusMemberName("Delete")
  fun delete(): DBusPath
}

/** `org.freedesktop.Secret.Session`. */
@DBusInterfaceName("org.freedesktop.Secret.Session")
internal interface SessionApi : DBusInterface {
  /** Ends the session. */
  @DBusMemberName("Close")
  fun close()
}

/** `org.freedesktop.Secret.Prompt`: asks the user, in the keyring's own dialog, to confirm an action. */
@DBusInterfaceName("org.freedesktop.Secret.Prompt")
internal interface PromptApi : DBusInterface {
  /** Shows the prompt; the outcome arrives as [Completed]. */
  @DBusMemberName("Prompt")
  fun prompt(windowId: String)

  /** Emitted when the prompt is answered or dismissed. */
  class Completed(path: String, @JvmField val dismissed: Boolean, @JvmField val result: Variant<*>) :
    DBusSignal(path, dismissed, result)
}

/** A secret on the wire: `(oayays)`. */
internal class Secret(
  // Read by dbus-java when it marshals the struct; nothing here needs to look at it.
  @Suppress("unused") @field:Position(0) @JvmField val session: DBusPath,
  @field:Position(1) @JvmField val parameters: ByteArray,
  @field:Position(2) @JvmField val value: ByteArray,
  @field:Position(CONTENT_TYPE_POSITION) @JvmField val contentType: String,
) : Struct()

/** Reply of [ServiceApi.openSession]. */
internal class OpenSessionResult(
  @field:Position(0) @JvmField val output: Variant<*>,
  @field:Position(1) @JvmField val session: DBusPath,
) : Tuple()

/** Reply of [ServiceApi.searchItems]. */
internal class SearchResult(
  @field:Position(0) @JvmField val unlocked: List<DBusPath>,
  @field:Position(1) @JvmField val locked: List<DBusPath>,
) : Tuple()

/** Reply of [ServiceApi.unlock]. */
internal class UnlockResult(
  // Filled in by dbus-java. Not checked: services differ in whether an already unlocked object is listed.
  @Suppress("unused") @field:Position(0) @JvmField val unlocked: List<DBusPath>,
  @field:Position(1) @JvmField val prompt: DBusPath,
) : Tuple()

/** Reply of [CollectionApi.createItem]. */
internal class CreateItemResult(
  @field:Position(0) @JvmField val item: DBusPath,
  @field:Position(1) @JvmField val prompt: DBusPath,
) : Tuple()

/** Position of the content type in a [Secret]. */
private const val CONTENT_TYPE_POSITION = 3

/** Bus name of the Secret Service. */
internal const val BUS_NAME = "org.freedesktop.secrets"

/** Object path of the Secret Service. */
internal const val SERVICE_PATH = "/org/freedesktop/secrets"
