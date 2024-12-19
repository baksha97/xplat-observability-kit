package monitorable

import monitorable.Monitor.Collector
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

interface Capturing {
    val collector: Collector

    fun <E> withCapture(
        key: String,
        closure: () -> E
    ): E =
        capture(key) { runCatching { closure() } }
        .value
        .getOrThrow()

    fun <E> withResultCapture(
        key: String,
        closure: () -> Result<E>
    ): Result<E> =
        capture(key) { closure() }
            .value

    private fun <T> capture(
        key: String,
        closure: () -> Result<T>
    ): TimedValue<Result<T>> {
        val measured = measureTimedValue {
            closure()
        }
        collector.invoke(
            Monitor.Data(
                key = key,
                durationMillis = measured.duration.inWholeMilliseconds,
                exception = measured.value.exceptionOrNull()
            )
        )
        return measured
    }
}