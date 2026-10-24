package team.elrant.bubbles.store

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

private val logger = LoggerFactory.getLogger("team.elrant.bubbles.store.OmemoFiles")

/**
 * Directory holding the OMEMO key material and trust decisions of [account] (`name@server`), created if missing.
 *
 * The keys inside are not encrypted. Instead the directory is made private to the current user (`rwx------`) where
 * the file system supports POSIX permissions, which also covers the files below it.
 */
internal fun omemoDirectory(account: String): Path {
  val directory = configFile("omemo").resolve(account)
  try {
    Files.createDirectories(directory)
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
  } catch (e: UnsupportedOperationException) {
    logger.warn("Cannot restrict the permissions of {}: {}", directory, e.message)
  } catch (e: IOException) {
    logger.warn("Could not prepare {}: {}", directory, e.message)
  }
  return directory
}
