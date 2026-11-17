import dev.droidtop.plugins.shizuku.ShizukuFacets
import dev.droidtop.plugins.shizuku.ShizukuFacets.State

private fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
    println("ok: $what")
}

fun main() {
    check(ShizukuFacets.stateOf(installed = false, running = false, allowed = false) == State.NOT_INSTALLED, "not installed")
    check(ShizukuFacets.stateOf(installed = true, running = false, allowed = false) == State.NOT_RUNNING, "not running")
    check(ShizukuFacets.stateOf(installed = true, running = true, allowed = false) == State.NOT_ALLOWED, "not allowed")
    check(ShizukuFacets.stateOf(installed = true, running = true, allowed = true) == State.READY, "ready")

    // The one step that makes Shizuku usable is offered in place, on both targets.
    for (target in listOf(ShizukuFacets.QUICK_MENU, ShizukuFacets.SETTINGS)) {
        check(ShizukuFacets.plan(target, State.NOT_RUNNING, false).any { it.op == ShizukuFacets.OP_OPEN }, "$target offers Open Shizuku while it is not running")
        check(ShizukuFacets.plan(target, State.NOT_ALLOWED, false).any { it.op == ShizukuFacets.OP_ALLOW }, "$target offers Allow droidtop while it is not allowed")
        check(ShizukuFacets.plan(target, State.READY, true).none { it.op != null }, "$target offers no step once ready")
    }
    // The Quick Menu takes a status; the settings page takes an info line and a notice (FacetGuards).
    check(ShizukuFacets.plan(ShizukuFacets.QUICK_MENU, State.READY, true).single().let { it.kind == "status" && it.value == "Running as root, allowed" }, "quick menu status says root")
    check(ShizukuFacets.plan(ShizukuFacets.SETTINGS, State.READY, false).single().kind == "info", "settings line is an info")
    check(ShizukuFacets.plan(ShizukuFacets.SETTINGS, State.NOT_INSTALLED, false).any { it.kind == "notice" }, "settings says it is not installed")
    check(ShizukuFacets.plan(ShizukuFacets.QUICK_MENU, State.NOT_INSTALLED, false).none { it.kind == "notice" }, "quick menu takes no notice")
    check(ShizukuFacets.plan("game.page", State.READY, false).isEmpty(), "no facets for a target it does not use")
}
