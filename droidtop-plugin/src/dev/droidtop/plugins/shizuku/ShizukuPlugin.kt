package dev.droidtop.plugins.shizuku

import android.content.pm.PackageManager
import dev.droidtop.pluginhost.DroidtopPlugin
import dev.droidtop.pluginhost.LegacyHandle
import dev.droidtop.pluginhost.PluginArgs
import dev.droidtop.pluginhost.PluginCall
import dev.droidtop.pluginhost.PluginCapability
import dev.droidtop.pluginhost.PluginContext
import dev.droidtop.pluginhost.PluginErrorCode
import dev.droidtop.pluginhost.PluginReply
import dev.droidtop.pluginhost.PluginResult
import java.io.InputStream
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku

/**
 * Shizuku (github.com/RikkaApps/Shizuku, Apache-2.0) as a droidtop plugin,
 * and droidtop's provider of ADB-level privilege (docs/plugin-api.md 2.7).
 *
 * Two jobs:
 *
 * - **Provider.** It exports two standard interfaces, each running one
 *   command through Shizuku's server:
 *   `priv.packages@1` (`force_stop {package}`, which is what lets Quit to
 *   Library end another app's game where Android 13 gives droidtop no way to)
 *   and `priv.shell@1` (`exec {argv}`), the latter at two levels, each its own
 *   grant: "adb" (Shizuku's server runs as the ADB shell user) and "root"
 *   (Shizuku's server runs as uid 0: started as root, or Sui). droidtop's
 *   broker has already checked the caller's own grant for the op's permission
 *   (`priv.shell.adb` or `priv.shell.root`, carried in `caller.grants`) before
 *   `handle` is called, so this class applies only what the broker cannot
 *   know: is Shizuku running, is droidtop allowed in it, and as which user
 *   its server runs. It reports that last one to droidtop
 *   (`plugins.report_level`), which offers the root export only while it is
 *   "root" (docs/plugin-api.md 2.7). droidtop itself never runs `su`.
 * - **The surface for Shizuku itself.** A status tile and an app_status page,
 *   as before: is it installed, running and allowed, and a way to open
 *   Shizuku's own pairing screen or ask it to allow droidtop.
 *
 * How the binder gets here: Shizuku's server pushes it to a content provider
 * named `<droidtop's applicationId>.shizuku`. Droidtop's :plugin-host declares
 * Shizuku's own `ShizukuProvider` under that name in this plugin's process
 * and carries Shizuku's client library, and this plugin's class loader
 * delegates to the host's first, so `rikka.shizuku.Shizuku` below is the
 * copy that holds the binder. Nothing here bundles Shizuku's client.
 *
 * Plugin runs in droidtop's own process (full trust, not contained): the
 * Shizuku permission is granted to droidtop's UID, which is why this plugin
 * declares `host.full_trust` and why it can only be a provider, never a
 * requirement of another plugin's core function.
 */
class ShizukuPlugin : DroidtopPlugin {
    private lateinit var context: PluginContext

    override fun onLoad(context: PluginContext) {
        this.context = context
        // Off the load: the first report is a broker round trip and Shizuku may not have sent its binder yet.
        Thread({ reportLevel() }, "shizuku-level").apply { isDaemon = true }.start()
    }

    // ---- the level Shizuku's server runs at ------------------------------

    /** The last level droidtop accepted, so an unchanged level is not re-sent on every call. */
    @Volatile private var reported: String? = null

    /** "root" while Shizuku's server is uid 0, "adb" while it is the shell user, "none" while it cannot be used. */
    private fun serverLevel(): String = runCatching {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            LEVEL_NONE
        } else if (Shizuku.getUid() == ROOT_UID) {
            LEVEL_ROOT
        } else {
            LEVEL_ADB
        }
    }.getOrDefault(LEVEL_NONE)

    /**
     * Tells droidtop which level the `priv.shell` root export may be offered at right now. An older droidtop without
     * `plugins.report_level` refuses it, and then the root export is simply never offered there.
     */
    private fun reportLevel(level: String = serverLevel()): String {
        if (level != reported) {
            val ok = runCatching {
                JSONObject(context.call("plugins", 1, "report_level", JSONObject().put("api", "priv.shell").put("level", level).toString())).optBoolean("ok")
            }.getOrDefault(false)
            if (ok) reported = level
        }
        return level
    }

    override fun invoke(capability: PluginCapability, args: PluginArgs): PluginResult = when (capability) {
        PluginCapability.STATUS_TILE -> statusTile()
        PluginCapability.APP_STATUS -> appStatus(args)
        else -> PluginResult.failure("ShizukuPlugin does not implement ${capability.id}")
    }

    /** v2 calls: the two exported APIs here, everything else (the tile, the app page) through the contract 1 path above. */
    override fun handle(call: PluginCall): PluginReply = when (call.point) {
        "api:priv.packages" -> when (call.op) {
            "force_stop" -> forceStop(call)
            else -> PluginReply.error(PluginErrorCode.UNSUPPORTED, "priv.packages has no op ${call.op}")
        }
        "api:priv.shell" -> when (call.op) {
            "exec" -> exec(call)
            else -> PluginReply.error(PluginErrorCode.UNSUPPORTED, "priv.shell has no op ${call.op}")
        }
        else -> LegacyHandle.translate(this, call)
    }

    // ---- provider ------------------------------------------------------

    /** Null when Shizuku can be used right now, otherwise the reply that says why not, in words a person can act on. */
    private fun notReady(): PluginReply? {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            return PluginReply.error(PluginErrorCode.PROVIDER_UNAVAILABLE, "Shizuku is not running. Open Shizuku and start it.")
        }
        val allowed = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (!allowed) {
            return PluginReply.error(PluginErrorCode.PERMISSION_DENIED, "droidtop is not allowed in Shizuku. Open Shizuku and allow droidtop.")
        }
        return null
    }

    private fun forceStop(call: PluginCall): PluginReply {
        val target = call.args.optString("package")
        if (!PACKAGE_NAME.matches(target)) return PluginReply.error(PluginErrorCode.INVALID_ARGS, "package must be a package name")
        notReady()?.let { return it }
        val outcome = run(listOf("am", "force-stop", target), minOf(STOP_TIMEOUT_MS, budget(call)))
            ?: return PluginReply.error(PluginErrorCode.TIMEOUT, "Shizuku did not answer in time")
        return if (outcome.exit == 0) {
            PluginReply.ok(JSONObject().put("stopped", true))
        } else {
            PluginReply.error(PluginErrorCode.FAILED, outcome.stderr.ifBlank { "am force-stop exited with ${outcome.exit}" })
        }
    }

    private fun exec(call: PluginCall): PluginReply {
        val array = call.args.optJSONArray("argv")
        val argv = if (array == null) emptyList() else List(array.length()) { array.optString(it) }
        if (argv.isEmpty() || argv.size > MAX_ARGS || argv.any { it.isEmpty() || it.length > MAX_ARG_LENGTH }) {
            return PluginReply.error(PluginErrorCode.INVALID_ARGS, "argv must list 1 to $MAX_ARGS non-empty arguments")
        }
        notReady()?.let { return it }
        levelRefusal(call)?.let { return it }
        val wanted = call.args.optLong("timeoutMs", DEFAULT_EXEC_TIMEOUT_MS).coerceIn(500L, MAX_EXEC_TIMEOUT_MS)
        val outcome = run(argv, minOf(wanted, budget(call)))
            ?: return PluginReply.error(PluginErrorCode.TIMEOUT, "the command did not finish in time")
        return PluginReply.ok(JSONObject().put("exit", outcome.exit).put("stdout", outcome.stdout).put("stderr", outcome.stderr))
    }

    /**
     * Null when this exec may run at the level Shizuku's server runs at, else why not. A caller allowed root
     * (`priv.shell.root`) is served only while the server is uid 0. A caller allowed only the system level
     * (`priv.shell.adb`) is NOT served while the server is root: Shizuku runs every command as its own user and there is
     * no way to drop to the shell user without `su`, so running it would give that caller root it was never allowed.
     */
    private fun levelRefusal(call: PluginCall): PluginReply? {
        val grants = call.caller.optJSONArray("grants")
        val wantsRoot = grants != null && (0 until grants.length()).any { grants.optString(it) == "priv.shell.root" }
        val level = reportLevel()
        return when {
            wantsRoot && level != LEVEL_ROOT ->
                PluginReply.error(PluginErrorCode.PROVIDER_UNAVAILABLE, "Shizuku is not running as root, so it cannot run commands as root.")
            !wantsRoot && level == LEVEL_ROOT ->
                PluginReply.error(
                    PluginErrorCode.PERMISSION_DENIED,
                    "Shizuku is running as root, and this plugin is only allowed to run commands as the system (adb). Allow it \"Run commands as root\" if you trust it.",
                )
            else -> null
        }
    }

    /** The time this call may take: the deadline droidtop gave it, less a margin to answer in. */
    private fun budget(call: PluginCall): Long = (call.deadlineMs - REPLY_MARGIN_MS).coerceAtLeast(MIN_BUDGET_MS)

    private class Outcome(val exit: Int, val stdout: String, val stderr: String)

    /**
     * Runs [argv] through Shizuku, directly (never through a shell). Null when it does not finish within [timeoutMs].
     * `Shizuku.newProcess` is private since Shizuku 13, so it is reached by reflection, the same way other apps do.
     */
    private fun run(argv: List<String>, timeoutMs: Long): Outcome? {
        val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        val process = method.invoke(null, argv.toTypedArray(), null, null) as Process
        val out = Capture(process.inputStream)
        val err = Capture(process.errorStream)
        out.start()
        err.start()
        return try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy()
                null
            } else {
                out.join(STREAM_JOIN_MS)
                err.join(STREAM_JOIN_MS)
                Outcome(process.exitValue(), out.text(), err.text())
            }
        } finally {
            runCatching { process.destroy() }
        }
    }

    /** Reads one stream on its own thread so a full pipe never stalls the command, keeping the first [MAX_STREAM_CHARS] characters. */
    private class Capture(private val stream: InputStream) : Thread() {
        private val text = StringBuilder()

        override fun run() {
            val buffer = CharArray(4096)
            runCatching {
                stream.bufferedReader().use { reader ->
                    while (true) {
                        val n = reader.read(buffer)
                        if (n < 0) break
                        synchronized(text) { if (text.length < MAX_STREAM_CHARS) text.append(buffer, 0, minOf(n, MAX_STREAM_CHARS - text.length)) }
                    }
                }
            }
        }

        fun text(): String = synchronized(text) { text.toString().trim() }
    }

    // ---- the surface for Shizuku itself --------------------------------

    private fun currentState(): String {
        if (!context.isAppInstalled(SHIZUKU_MANAGER_PACKAGE)) return STATE_NOT_INSTALLED
        return if (context.hasShizukuAccess()) STATE_GRANTED else STATE_NOT_GRANTED
    }

    private fun statusTile(): PluginResult {
        val value = when (currentState()) {
            STATE_NOT_INSTALLED -> "Not installed"
            STATE_GRANTED -> if (reportLevel() == LEVEL_ROOT) "Running as root, allowed" else "Running, allowed"
            else -> "Installed, not allowed"
        }
        return PluginResult.success(mapOf("label" to "Shizuku", "value" to value))
    }

    /**
     * [PluginCapability.APP_STATUS] doubles as both "what do you know
     * about this package" (no `action` arg: state only) and "do
     * something about it": [ACTION_START] and [ACTION_OPEN_SETUP] open
     * Shizuku's own manager (its one Activity is the pairing and start
     * screen, and there is no separate "start the service" command
     * without root or an ADB shell droidtop has no business running
     * itself), and [ACTION_GRANT] asks Shizuku to allow droidtop, which
     * Shizuku answers with its own dialog.
     */
    private fun appStatus(args: PluginArgs): PluginResult {
        val state = currentState()
        val base = mapOf("package" to SHIZUKU_MANAGER_PACKAGE, "state" to state)
        return when (val action = args.string("action")) {
            null, "", "status" -> PluginResult.success(base)
            ACTION_START, ACTION_OPEN_SETUP -> {
                if (state == STATE_NOT_INSTALLED) {
                    PluginResult.failure("Shizuku is not installed")
                } else if (context.launchApp(SHIZUKU_MANAGER_PACKAGE)) {
                    PluginResult.success(base)
                } else {
                    PluginResult.failure("could not launch Shizuku")
                }
            }
            ACTION_GRANT -> when {
                state == STATE_NOT_INSTALLED -> PluginResult.failure("Shizuku is not installed")
                !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> PluginResult.failure("Shizuku is not running. Open it and start it first.")
                state == STATE_GRANTED -> PluginResult.success(base)
                else -> {
                    runCatching { Shizuku.requestPermission(REQUEST_CODE) }
                    PluginResult.success(base + ("note" to "Shizuku is asking you to allow droidtop"))
                }
            }
            else -> PluginResult.failure("unknown action \"$action\"")
        }
    }

    companion object {
        const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        const val STATE_NOT_INSTALLED = "not_installed"
        const val STATE_NOT_GRANTED = "installed_not_granted"
        const val STATE_GRANTED = "granted"
        const val ACTION_START = "start"
        const val ACTION_OPEN_SETUP = "open_setup"
        const val ACTION_GRANT = "grant"
        private const val REQUEST_CODE = 1

        private const val ROOT_UID = 0
        private const val LEVEL_NONE = "none"
        private const val LEVEL_ADB = "adb"
        private const val LEVEL_ROOT = "root"

        private const val MAX_ARGS = 64
        private const val MAX_ARG_LENGTH = 4096
        private const val MAX_STREAM_CHARS = 64 * 1024
        private const val STREAM_JOIN_MS = 500L
        private const val STOP_TIMEOUT_MS = 8_000L
        private const val DEFAULT_EXEC_TIMEOUT_MS = 10_000L
        private const val MAX_EXEC_TIMEOUT_MS = 60_000L
        private const val REPLY_MARGIN_MS = 500L
        private const val MIN_BUDGET_MS = 1_000L

        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+\$")
    }
}
