package dev.droidtop.plugins.shizuku

/**
 * Waits for a process Shizuku's server runs for the plugin. `ShizukuRemoteProcess` does not override
 * `Process.waitFor(timeout, unit)`, so Java's default runs: it polls `exitValue()` and takes only an
 * `IllegalThreadStateException` as "still running". The server throws that, but Binder delivers it as its parent,
 * `IllegalArgumentException("process hasn't exited")`, so the first poll of a running command threw out of the plugin
 * and droidtop disabled it ("Stopped: The plugin failed to load", Droidtop/tracker#558). Same approach as droidtop's
 * own RemoteProcessWait (Droidtop/droidtop 7693a2a).
 */
object RemoteProcessWait {
    private const val POLL_MS = 20L

    /** [process]'s exit value once it has exited, or null when it has not within [timeoutMs]. [nowMs] and [sleep] are for tests. */
    fun exitWithin(
        process: Process,
        timeoutMs: Long,
        nowMs: () -> Long = System::currentTimeMillis,
        sleep: (Long) -> Unit = Thread::sleep,
    ): Int? {
        val deadline = nowMs() + timeoutMs
        while (true) {
            try {
                return process.exitValue()
            } catch (running: IllegalArgumentException) {
                // IllegalThreadStateException is one; across Binder it arrives as its parent.
            }
            val left = deadline - nowMs()
            if (left <= 0) return null
            sleep(minOf(POLL_MS, left))
        }
    }
}
