package dev.droidtop.plugins.shizuku

/**
 * What droidtop draws for Shizuku itself, as facets (Droidtop/droidtop docs/plugin-api.md 1.10, "Facets first";
 * Droidtop/tracker#602): its state on the Quick Menu and its settings page, and the one step that makes it usable,
 * in place: Open Shizuku while it is not running (its own screen is where it is paired and started; nothing else can
 * start it without root or ADB), Allow droidtop while it runs and has not allowed droidtop. Pure, so the plan can be
 * tested without Android or org.json; [ShizukuPlugin] turns it into JSON.
 */
object ShizukuFacets {
    const val QUICK_MENU = "quickmenu"
    const val SETTINGS = "settings.page"
    const val OP_OPEN = "open"
    const val OP_ALLOW = "allow"

    /** Where Shizuku is: from its install state, whether its server answers, and as which user it runs. */
    enum class State { NOT_INSTALLED, NOT_RUNNING, NOT_ALLOWED, READY }

    /** One facet: a `status` (Quick Menu) or `info` (settings page) line, an `action`, or a `notice`. */
    data class Spec(
        val id: String,
        val kind: String,
        val label: String,
        val value: String? = null,
        val sub: String? = null,
        val state: String? = null,
        val tone: String? = null,
        val op: String? = null,
    )

    fun stateOf(installed: Boolean, running: Boolean, allowed: Boolean): State = when {
        !installed -> State.NOT_INSTALLED
        !running -> State.NOT_RUNNING
        !allowed -> State.NOT_ALLOWED
        else -> State.READY
    }

    fun words(state: State, root: Boolean): String = when (state) {
        State.NOT_INSTALLED -> "Not installed"
        State.NOT_RUNNING -> "Installed, not running"
        State.NOT_ALLOWED -> "Running, droidtop not allowed yet"
        State.READY -> if (root) "Running as root, allowed" else "Running, allowed"
    }

    /** The facets for [target]: none for a target the plugin does not use. */
    fun plan(target: String, state: State, root: Boolean): List<Spec> {
        if (target != QUICK_MENU && target != SETTINGS) return emptyList()
        val line = if (target == QUICK_MENU) {
            Spec(
                "state", "status", "Shizuku", words(state, root),
                state = if (state == State.READY) "ok" else if (state == State.NOT_INSTALLED) "off" else "warning",
                tone = if (state == State.READY) "positive" else "caution",
            )
        } else {
            Spec("state", "info", "Shizuku", words(state, root))
        }
        val step = when (state) {
            State.NOT_RUNNING -> Spec("open", "action", "Open Shizuku", sub = "Start it there: with Wireless debugging, or as root", op = OP_OPEN)
            State.NOT_ALLOWED -> Spec("allow", "action", "Allow droidtop", sub = "Shizuku asks you to allow droidtop", op = OP_ALLOW)
            // There is no way to install another app from a facet yet (Droidtop/tracker#602, the audit's gap 6).
            State.NOT_INSTALLED -> if (target == SETTINGS) {
                Spec("missing", "notice", "Shizuku is not installed", sub = "Install Shizuku from your app store or F-Droid, then come back here")
            } else {
                null
            }
            State.READY -> null
        }
        return listOfNotNull(line, step)
    }
}
