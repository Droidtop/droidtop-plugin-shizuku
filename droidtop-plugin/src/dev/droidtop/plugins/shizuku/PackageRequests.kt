package dev.droidtop.plugins.shizuku

/**
 * The request checks of `priv.packages@1` (docs/plugin-api.md 2.7, Droidtop/tracker#130): what each op accepts and the
 * exact command it becomes. Pure, so they are tested without a device; [ShizukuPlugin] runs only what these build.
 * A package name, an AppOps name or a permission name is checked against the shape the command takes, and each is one
 * argument of the command, never shell text.
 */
object PackageRequests {
    /**
     * What a request becomes: the command to run, or why it is refused, with the error code it is answered with
     * (`INVALID_ARGS` for a shape, `NOT_FOUND` for an app that is not there, `PERMISSION_DENIED` for one never touched).
     */
    sealed class Check {
        data class Run(val argv: List<String>) : Check()
        data class Refused(val message: String, val code: String = INVALID_ARGS) : Check()
    }

    const val INVALID_ARGS = "INVALID_ARGS"
    const val NOT_FOUND = "NOT_FOUND"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"

    val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+\$")
    val APPOP_NAME = Regex("^[A-Z][A-Z0-9_]{0,63}\$")
    val APPOP_MODES = listOf("allow", "ignore", "deny", "default")
    val PERMISSION_NAME = Regex("^[A-Za-z][A-Za-z0-9_.]{0,199}\$")

    /** Shizuku's own manager app: removing or clearing it ends every privileged call, including this one. */
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private fun packageOf(name: String?): String? = name?.takeIf { PACKAGE_NAME.matches(it) }

    private val notAPackage = Check.Refused("package must be a package name")

    fun forceStop(pkg: String?): Check = packageOf(pkg)?.let { Check.Run(listOf("am", "force-stop", it)) } ?: notAPackage

    fun setAppOp(pkg: String?, op: String?, mode: String?): Check {
        val target = packageOf(pkg) ?: return notAPackage
        if (op == null || !APPOP_NAME.matches(op)) return Check.Refused("op must be an AppOps name such as MANAGE_EXTERNAL_STORAGE")
        if (mode !in APPOP_MODES) return Check.Refused("mode must be one of ${APPOP_MODES.joinToString(", ")}")
        return Check.Run(listOf("appops", "set", target, op, mode!!))
    }

    fun grantPermission(pkg: String?, permission: String?): Check = permissionCommand("grant", pkg, permission)

    /** `pm revoke`, the pair to [grantPermission]: Android refuses a permission that is not a runtime one. */
    fun revokePermission(pkg: String?, permission: String?): Check = permissionCommand("revoke", pkg, permission)

    private fun permissionCommand(verb: String, pkg: String?, permission: String?): Check {
        val target = packageOf(pkg) ?: return notAPackage
        if (permission == null || !PERMISSION_NAME.matches(permission)) return Check.Refused("permission must be a permission name")
        return Check.Run(listOf("pm", verb, target, permission))
    }

    /**
     * The apps an uninstall or a data wipe never touches, read on the device before either runs: droidtop itself
     * ([ownUid] is droidtop's process, and every package of that uid is droidtop), Shizuku, whatever Android opens
     * on Home right now (the launcher the active mode uses: droidtop's, or the one the person chose), and an app
     * that is part of the system image.
     */
    data class Device(val ownUid: Int, val targetUid: Int?, val homePackage: String?, val targetIsSystem: Boolean)

    /** The commands that read [Device] for [pkg], in order: its uid, the Home app, whether it is a system app. */
    fun deviceQueries(pkg: String): List<List<String>> = listOf(
        listOf("pm", "list", "packages", "-U", pkg),
        listOf("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"),
        listOf("pm", "list", "packages", "-s", pkg),
    )

    /** `uid` of exactly [pkg] in `pm list packages -U` output (`package:<name> uid:<n>`, one per line), or null. */
    fun uidIn(listing: String, pkg: String): Int? = listing.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("package:$pkg ") }
        ?.substringAfter(" uid:", "")?.substringBefore(',')?.trim()?.toIntOrNull()

    /** Whether exactly [pkg] is in `pm list packages` output (`package:<name>` lines; the filter matches substrings). */
    fun listed(listing: String, pkg: String): Boolean = listing.lineSequence().any { it.trim() == "package:$pkg" }

    /** The package of `resolve-activity --brief`'s answer (its last line, `<package>/<class>`), or null for none or a chooser. */
    fun homeIn(resolved: String): String? = resolved.lineSequence().map { it.trim() }.lastOrNull { it.contains('/') }
        ?.substringBefore('/')?.takeIf { PACKAGE_NAME.matches(it) && it != "android" }

    /** Why [pkg] is not one to remove or wipe on [device], or null when it may be. */
    fun protectedReason(pkg: String, device: Device): Check.Refused? = when {
        device.targetUid == null -> Check.Refused("$pkg is not installed.", NOT_FOUND)
        device.targetUid == device.ownUid -> Check.Refused("droidtop does not remove or wipe itself.", PERMISSION_DENIED)
        pkg == SHIZUKU_PACKAGE -> Check.Refused("Shizuku does not remove or wipe itself.", PERMISSION_DENIED)
        pkg == device.homePackage -> Check.Refused("$pkg is the home screen in use.", PERMISSION_DENIED)
        device.targetIsSystem -> Check.Refused("$pkg is part of the system.", PERMISSION_DENIED)
        else -> null
    }

    /** `pm clear <package>`: every file and setting of the app, as if it were just installed. */
    fun clearData(pkg: String?, device: (String) -> Device): Check = guarded(pkg, device) { listOf("pm", "clear", it) }

    /** `pm uninstall --user 0 <package>`: the app is removed for the person using the device. */
    fun uninstall(pkg: String?, device: (String) -> Device): Check = guarded(pkg, device) { listOf("pm", "uninstall", "--user", "0", it) }

    private fun guarded(pkg: String?, device: (String) -> Device, command: (String) -> List<String>): Check {
        val target = packageOf(pkg) ?: return notAPackage
        protectedReason(target, device(target))?.let { return it }
        return Check.Run(command(target))
    }

    /** What droidtop's broker staged for one install (docs/plugin-api.md 2.7): never a path a caller chose. */
    data class Staged(val path: String, val size: Long, val sha256: String, val pkg: String)

    private val STAGED_NAME = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.apk\$")
    private val SHA256 = Regex("^[0-9a-f]{64}\$")

    /**
     * `install {staged}`: `pm install -S <size>`, the staged file streamed on stdin. The file must be directly in
     * droidtop's own staging folder [stagingDir] (`<droidtop's cache>/priv-install`) under the name droidtop gives it,
     * and its size and digest must be what droidtop said; so an older droidtop that passed a caller's args through
     * unread can never hand over a file droidtop did not stage. Shizuku is never replaced, nor an installed app that
     * [protectedReason] protects; an app that is not installed yet is the ordinary case.
     */
    fun install(staged: Staged?, stagingDir: java.io.File, device: (String) -> Device): Check {
        staged ?: return Check.Refused("staged is droidtop's to write: this droidtop does not install apps through a plugin")
        val pkg = packageOf(staged.pkg) ?: return notAPackage
        if (staged.size <= 0 || !SHA256.matches(staged.sha256)) return Check.Refused("staged must carry the size and digest droidtop read")
        val file = java.io.File(staged.path)
        val inStaging = runCatching { file.canonicalFile.parentFile == stagingDir.canonicalFile }.getOrDefault(false)
        if (!file.isAbsolute || !inStaging || !STAGED_NAME.matches(file.name)) {
            return Check.Refused("Only an app package droidtop staged is installed.", PERMISSION_DENIED)
        }
        if (pkg == SHIZUKU_PACKAGE) return Check.Refused("Shizuku does not replace itself.", PERMISSION_DENIED)
        val known = device(pkg)
        if (known.targetUid != null) protectedReason(pkg, known)?.let { return it }
        if (!file.isFile || file.length() != staged.size || sha256(file) != staged.sha256) {
            return Check.Refused("The staged app package is not the one droidtop checked.", PERMISSION_DENIED)
        }
        return Check.Run(listOf("pm", "install", "-S", staged.size.toString()))
    }

    fun sha256(file: java.io.File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** The one plain sentence for `pm install`'s `Failure [CODE: ...]` line about [pkg]; the line itself is the detail. */
    fun installFailure(output: String, pkg: String): String {
        val code = Regex("Failure \\[([A-Z_]+)").find(output)?.groupValues?.get(1)
        return when (code) {
            "INSTALL_FAILED_VERSION_DOWNGRADE" -> "A newer version of $pkg is already installed."
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE" -> "The installed $pkg is signed by someone else. Uninstall it first."
            "INSTALL_FAILED_INSUFFICIENT_STORAGE" -> "There is not enough space to install $pkg."
            "INSTALL_FAILED_OLDER_SDK" -> "$pkg needs a newer Android than this device has."
            "INSTALL_FAILED_NO_MATCHING_ABIS" -> "$pkg is not built for this device's processor."
            else -> "Android did not install $pkg."
        }
    }

    /** `pm clear` and `pm uninstall` say `Success` on their last line; anything else is the reason they did not. */
    fun succeeded(exit: Int, output: String): Boolean = exit == 0 && output.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } == "Success"
}
