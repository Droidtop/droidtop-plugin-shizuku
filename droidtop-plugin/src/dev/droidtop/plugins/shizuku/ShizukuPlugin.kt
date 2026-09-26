package dev.droidtop.plugins.shizuku

import dev.droidtop.pluginhost.DroidtopPlugin
import dev.droidtop.pluginhost.PluginArgs
import dev.droidtop.pluginhost.PluginCapability
import dev.droidtop.pluginhost.PluginContext
import dev.droidtop.pluginhost.PluginResult

/**
 * Shizuku (github.com/RikkaApps/Shizuku, Apache-2.0) as a droidtop
 * plugin. droidtop never bundles or launches Shizuku's own privileged
 * server -- that stays the separate app the user installs from Play or
 * GitHub and pairs themselves, same as any other app that supports
 * Shizuku today (see this repo's PLUGIN-PLAN.md). What this plugin adds
 * is the missing visible surface: droidtop's plugin host already answers
 * "is Shizuku available and granted" generically for any plugin
 * (PluginContext.hasShizukuAccess), but nothing shows a user that state,
 * or gets them to Shizuku's own pairing screen, without this.
 *
 * This plugin holds no bound connection of its own and brokers nothing
 * for other plugins: any OTHER plugin that wants a privileged Shizuku
 * call depends on Shizuku's own `api` module directly and gates it on
 * the same PluginContext.hasShizukuAccess() droidtop's host already
 * exposes to every plugin (see PLUGIN-PLAN.md's "Reuse as a native
 * bundle" section) -- this one is only the status/action surface for
 * Shizuku itself, exercised through droidtop's Settings -> App
 * integrations -> Plugins screen like any other native_bundle plugin.
 */
class ShizukuPlugin : DroidtopPlugin {
    private lateinit var context: PluginContext

    override fun onLoad(context: PluginContext) {
        this.context = context
    }

    override fun invoke(capability: PluginCapability, args: PluginArgs): PluginResult = when (capability) {
        PluginCapability.STATUS_TILE -> statusTile()
        PluginCapability.APP_STATUS -> appStatus(args)
        else -> PluginResult.failure("ShizukuPlugin does not implement ${capability.id}")
    }

    private fun currentState(): String {
        if (!context.isAppInstalled(SHIZUKU_MANAGER_PACKAGE)) return STATE_NOT_INSTALLED
        return if (context.hasShizukuAccess()) STATE_GRANTED else STATE_NOT_GRANTED
    }

    private fun statusTile(): PluginResult {
        val value = when (currentState()) {
            STATE_NOT_INSTALLED -> "Not installed"
            STATE_GRANTED -> "Running, granted"
            else -> "Installed, not granted"
        }
        return PluginResult.success(mapOf("label" to "Shizuku", "value" to value))
    }

    /**
     * [PluginCapability.APP_STATUS] doubles as both "what do you know
     * about this package" (no `action` arg: state only) and "do
     * something about it" (`action` = [ACTION_START] or
     * [ACTION_OPEN_SETUP]). Both actions resolve to the same thing --
     * Shizuku's manager app has one Activity, and it IS the pairing/
     * start screen -- so there is no separate "start the service"
     * command to send without root or an ADB shell droidtop has no
     * business running on the plugin's behalf.
     */
    private fun appStatus(args: PluginArgs): PluginResult {
        val state = currentState()
        val base = mapOf("package" to SHIZUKU_MANAGER_PACKAGE, "state" to state)
        return when (val action = args.string("action")) {
            null, "" -> PluginResult.success(base)
            ACTION_START, ACTION_OPEN_SETUP -> {
                if (state == STATE_NOT_INSTALLED) {
                    PluginResult.failure("Shizuku is not installed")
                } else if (context.launchApp(SHIZUKU_MANAGER_PACKAGE)) {
                    PluginResult.success(base)
                } else {
                    PluginResult.failure("could not launch Shizuku")
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
    }
}
