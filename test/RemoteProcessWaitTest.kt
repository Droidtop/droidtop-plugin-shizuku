import dev.droidtop.plugins.shizuku.RemoteProcessWait
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** A process that exits with [code] after [pollsBeforeExit] polls, saying "still running" the way [running] does. */
class FakeProcess(private val code: Int, private val pollsBeforeExit: Int, private val running: () -> RuntimeException) : Process() {
    var polls = 0
    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun waitFor(): Int = code
    override fun exitValue(): Int {
        if (polls++ < pollsBeforeExit) throw running()
        return code
    }
    override fun destroy() = Unit
}

private fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
    println("ok: $what")
}

fun main() {
    var clock = 0L
    val now = { clock }
    val sleep = { ms: Long -> clock += ms }

    val remote = FakeProcess(3, 2) { IllegalArgumentException("process hasn't exited") }
    check(RemoteProcessWait.exitWithin(remote, 5_000, now, sleep) == 3 && remote.polls == 3, "remote process still running is waited for, then its exit value returned")

    val local = FakeProcess(7, 2) { IllegalThreadStateException("process hasn't exited") }
    check(RemoteProcessWait.exitWithin(local, 5_000, now, sleep) == 7, "a local process still running is waited for too")

    clock = 0L
    val stuck = FakeProcess(0, Int.MAX_VALUE) { IllegalArgumentException("process hasn't exited") }
    check(RemoteProcessWait.exitWithin(stuck, 1_000, now, sleep) == null && clock == 1_000L, "a process that never exits gives null after the whole timeout")

    val failed = try {
        RemoteProcessWait.exitWithin(FakeProcess(0, 1) { IllegalStateException("binder died") }, 1_000, now, sleep)
        false
    } catch (e: IllegalStateException) {
        true
    }
    check(failed, "any other failure is not taken for still running")
}
