package dev.droidtop.plugins.shizuku

import dev.droidtop.plugins.shizuku.PackageRequests.Check

/**
 * The request checks of `priv.settings@1` (docs/plugin-api.md 2.7, Droidtop/tracker#72): Android's `secure` and
 * `global` settings, read and changed with the `settings` command. Pure, so they are tested without a device;
 * [ShizukuPlugin] runs only what these build. The namespace, the key and the value are each one argument of the
 * command, never shell text.
 */
object SettingsRequests {
    /** The tables the API covers. `system` is an app's to change with WRITE_SETTINGS, not a privileged call. */
    val NAMESPACES = listOf("secure", "global")

    val KEY = Regex("^[A-Za-z0-9_.:-]{1,128}\$")

    const val MAX_VALUE = 4096

    /**
     * Keys a change would cut the ground from under Shizuku itself (USB and wireless debugging, developer options):
     * Shizuku started over adb stops when they go off, mid-call. Read freely; never changed or deleted here.
     */
    val KEPT = setOf(
        "global" to "adb_enabled",
        "global" to "adb_wifi_enabled",
        "global" to "development_settings_enabled",
    )

    private fun shape(namespace: String?, key: String?): Check.Refused? = when {
        namespace !in NAMESPACES -> Check.Refused("namespace must be one of ${NAMESPACES.joinToString(", ")}")
        key == null || !KEY.matches(key) -> Check.Refused("key must be a setting's name")
        else -> null
    }

    private fun kept(namespace: String, key: String): Check.Refused? =
        if ((namespace to key) in KEPT) Check.Refused("$namespace $key is how Shizuku reaches the device; it is not changed through Shizuku.", PackageRequests.PERMISSION_DENIED) else null

    /** `settings get <namespace> <key>`. */
    fun get(namespace: String?, key: String?): Check =
        shape(namespace, key) ?: Check.Run(listOf("settings", "get", namespace!!, key!!))

    /** `settings put <namespace> <key> <value>`: the value is one argument, at most [MAX_VALUE] characters, one line. */
    fun put(namespace: String?, key: String?, value: String?): Check {
        shape(namespace, key)?.let { return it }
        kept(namespace!!, key!!)?.let { return it }
        if (value == null) return Check.Refused("value must be a string; use delete to remove a setting")
        if (value.length > MAX_VALUE) return Check.Refused("value is longer than $MAX_VALUE characters")
        if (value.any { it == '\n' || it == '\r' || it == '\u0000' }) return Check.Refused("value must be one line")
        return Check.Run(listOf("settings", "put", namespace, key, value))
    }

    /** `settings delete <namespace> <key>`. */
    fun delete(namespace: String?, key: String?): Check {
        shape(namespace, key)?.let { return it }
        kept(namespace!!, key!!)?.let { return it }
        return Check.Run(listOf("settings", "delete", namespace, key))
    }

    /** What `settings get` printed: its one line, or null for a setting that is not set (it prints `null`). */
    fun valueIn(output: String): String? = output.trimEnd('\n', '\r').takeUnless { it == "null" }

    /** Whether `settings delete` removed a row (`Deleted 1 rows`); 0 rows means it was not set. */
    fun deletedIn(output: String): Boolean =
        Regex("Deleted (\\d+) rows?").find(output)?.groupValues?.get(1)?.toIntOrNull()?.let { it > 0 } ?: false
}
