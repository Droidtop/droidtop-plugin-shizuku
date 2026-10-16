import dev.droidtop.plugins.shizuku.PackageRequests
import dev.droidtop.plugins.shizuku.PackageRequests.Check
import dev.droidtop.plugins.shizuku.PackageRequests.Device

private fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
    println("ok: $what")
}

private fun refused(c: Check, code: String = PackageRequests.INVALID_ARGS) = c is Check.Refused && c.code == code

private const val OWN_UID = 10100

/** A device where [pkg] is an ordinary app the person installed, unless the arguments say otherwise. */
private fun device(uid: Int? = 10200, home: String? = "com.android.launcher3", system: Boolean = false): (String) -> Device =
    { Device(OWN_UID, uid, home, system) }

fun main() {
    // Shapes: every op refuses what is not a package, an AppOps name, a mode or a permission name, before running anything.
    for (bad in listOf(null, "", "nodots", "1a.b", "a.b;rm -rf /", "a.b c", "-a.b", "a..b")) {
        check(refused(PackageRequests.forceStop(bad)), "force_stop refuses package \"$bad\"")
        check(refused(PackageRequests.clearData(bad, device())), "clear_data refuses package \"$bad\"")
        check(refused(PackageRequests.uninstall(bad, device())), "uninstall refuses package \"$bad\"")
        check(refused(PackageRequests.revokePermission(bad, "android.permission.CAMERA")), "revoke_permission refuses package \"$bad\"")
    }
    check(PackageRequests.forceStop("org.ppsspp.ppsspp") == Check.Run(listOf("am", "force-stop", "org.ppsspp.ppsspp")), "force_stop runs am force-stop")
    check(refused(PackageRequests.setAppOp("a.b", "manage_external_storage", "allow")), "set_appop refuses a lower-case op")
    check(refused(PackageRequests.setAppOp("a.b", "MANAGE_EXTERNAL_STORAGE", "always")), "set_appop refuses an unknown mode")
    check(
        PackageRequests.setAppOp("a.b", "MANAGE_EXTERNAL_STORAGE", "allow") == Check.Run(listOf("appops", "set", "a.b", "MANAGE_EXTERNAL_STORAGE", "allow")),
        "set_appop runs appops set",
    )
    check(refused(PackageRequests.grantPermission("a.b", "android.permission.CAMERA; reboot")), "grant_permission refuses a permission with spaces")
    check(refused(PackageRequests.revokePermission("a.b", "")), "revoke_permission refuses an empty permission")
    check(
        PackageRequests.revokePermission("a.b", "android.permission.CAMERA") == Check.Run(listOf("pm", "revoke", "a.b", "android.permission.CAMERA")),
        "revoke_permission runs pm revoke",
    )

    // What uninstall and clear_data never touch.
    val app = "com.example.throwaway"
    check(PackageRequests.uninstall(app, device()) == Check.Run(listOf("pm", "uninstall", "--user", "0", app)), "uninstall runs pm uninstall --user 0 for an ordinary app")
    check(PackageRequests.clearData(app, device()) == Check.Run(listOf("pm", "clear", app)), "clear_data runs pm clear for an ordinary app")
    check(refused(PackageRequests.uninstall(app, device(uid = null)), PackageRequests.NOT_FOUND), "an app that is not installed is NOT_FOUND")
    check(refused(PackageRequests.uninstall("dev.droidtop", device(uid = OWN_UID)), PackageRequests.PERMISSION_DENIED), "droidtop is never uninstalled")
    check(refused(PackageRequests.clearData("dev.droidtop", device(uid = OWN_UID)), PackageRequests.PERMISSION_DENIED), "droidtop's data is never cleared")
    check(refused(PackageRequests.uninstall(PackageRequests.SHIZUKU_PACKAGE, device()), PackageRequests.PERMISSION_DENIED), "Shizuku is never uninstalled")
    check(refused(PackageRequests.uninstall("com.teslacoilsw.launcher", device(home = "com.teslacoilsw.launcher")), PackageRequests.PERMISSION_DENIED), "the Home app in use is never uninstalled")
    check(refused(PackageRequests.clearData("com.teslacoilsw.launcher", device(home = "com.teslacoilsw.launcher")), PackageRequests.PERMISSION_DENIED), "the Home app in use is never cleared")
    check(refused(PackageRequests.uninstall("com.android.settings", device(system = true)), PackageRequests.PERMISSION_DENIED), "a system app is never uninstalled")

    // Reading the device.
    val listing = "package:com.example.throwaway.extra uid:10300\npackage:com.example.throwaway uid:10200\n"
    check(PackageRequests.uidIn(listing, app) == 10200, "the uid is the exact package's, not a longer name's")
    check(PackageRequests.uidIn("package:com.example.throwaway.extra uid:10300", app) == null, "a longer name alone is not the package")
    check(PackageRequests.uidIn("package:com.example.throwaway uid:10200,10201", app) == 10200, "a shared uid list reads its first uid")
    check(PackageRequests.listed("package:com.android.settings\n", "com.android.settings"), "a system listing names the package")
    check(!PackageRequests.listed("package:com.android.settings.intelligence\n", "com.android.settings"), "a longer system name is not the package")
    check(PackageRequests.homeIn("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\ndev.droidtop/dev.droidtop.shell.standard.HomeTrampolineActivity\n") == "dev.droidtop", "the Home app is read from resolve-activity")
    check(PackageRequests.homeIn("android/com.android.internal.app.ResolverActivity") == null, "the chooser is no Home app")
    check(PackageRequests.homeIn("No activity found") == null, "no answer is no Home app")
    check(PackageRequests.deviceQueries(app).all { app in it || "android.intent.category.HOME" in it }, "every query names the package or Home")

    // Results.
    check(PackageRequests.succeeded(0, "Success\n"), "Success succeeded")
    check(!PackageRequests.succeeded(0, "Failure [DELETE_FAILED_INTERNAL_ERROR]\n"), "a Failure line did not, even with exit 0")
    check(!PackageRequests.succeeded(1, "Success"), "a non-zero exit did not")
    println("PackageRequestsTest passed")
}
