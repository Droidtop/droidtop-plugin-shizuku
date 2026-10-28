import dev.droidtop.plugins.shizuku.PackageRequests
import dev.droidtop.plugins.shizuku.PackageRequests.Check
import dev.droidtop.plugins.shizuku.SettingsRequests

private fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
    println("ok: $what")
}

private fun refused(c: Check, code: String = PackageRequests.INVALID_ARGS) = c is Check.Refused && c.code == code

fun main() {
    // Shapes: only secure and global, and a key that is a setting's name, before anything runs.
    for (ns in listOf(null, "", "system", "SECURE", "secure;id")) {
        check(refused(SettingsRequests.get(ns, "location_mode")), "get refuses namespace \"$ns\"")
        check(refused(SettingsRequests.put(ns, "location_mode", "3")), "put refuses namespace \"$ns\"")
        check(refused(SettingsRequests.delete(ns, "location_mode")), "delete refuses namespace \"$ns\"")
    }
    for (key in listOf(null, "", "a b", "a;b", "a\$b", "x".repeat(129))) {
        check(refused(SettingsRequests.get("secure", key)), "get refuses key \"${key?.take(20)}\"")
        check(refused(SettingsRequests.put("secure", key, "1")), "put refuses key \"${key?.take(20)}\"")
    }
    check(SettingsRequests.get("secure", "location_mode") == Check.Run(listOf("settings", "get", "secure", "location_mode")), "get runs settings get")
    check(
        SettingsRequests.put("global", "window_animation_scale", "0.5") == Check.Run(listOf("settings", "put", "global", "window_animation_scale", "0.5")),
        "put runs settings put with the value as one argument",
    )
    check(
        SettingsRequests.put("secure", "enabled_input_methods", "a b; rm -rf /") == Check.Run(listOf("settings", "put", "secure", "enabled_input_methods", "a b; rm -rf /")),
        "a value with spaces and shell text stays one argument",
    )
    check(refused(SettingsRequests.put("secure", "k", null)), "put refuses no value")
    check(refused(SettingsRequests.put("secure", "k", "a\nb")), "put refuses a value of two lines")
    check(refused(SettingsRequests.put("secure", "k", "x".repeat(SettingsRequests.MAX_VALUE + 1))), "put refuses a value too long")
    check(SettingsRequests.delete("global", "k") == Check.Run(listOf("settings", "delete", "global", "k")), "delete runs settings delete")

    // What Shizuku stands on: read freely, never changed or deleted.
    check(SettingsRequests.get("global", "adb_enabled") is Check.Run, "adb_enabled can be read")
    check(refused(SettingsRequests.put("global", "adb_enabled", "0"), PackageRequests.PERMISSION_DENIED), "adb_enabled is not changed")
    check(refused(SettingsRequests.delete("global", "adb_wifi_enabled"), PackageRequests.PERMISSION_DENIED), "adb_wifi_enabled is not deleted")
    check(refused(SettingsRequests.put("global", "development_settings_enabled", "0"), PackageRequests.PERMISSION_DENIED), "developer options are not turned off")
    check(SettingsRequests.put("secure", "adb_enabled", "0") is Check.Run, "the kept keys are global ones only")

    // Reading the command's answers.
    check(SettingsRequests.valueIn("null\n") == null, "an unset setting reads as null")
    check(SettingsRequests.valueIn("3\n") == "3", "a value is its line")
    check(SettingsRequests.valueIn("") == "", "an empty value stays empty, not null")
    check(SettingsRequests.deletedIn("Deleted 1 rows\n"), "delete saw a row removed")
    check(!SettingsRequests.deletedIn("Deleted 0 rows\n"), "delete of an unset setting removed nothing")
}
