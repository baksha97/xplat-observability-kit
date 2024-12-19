package monitorable

import monitorable.Monitor.Collector
import kotlin.time.measureTimedValue

interface Capturing {
    val collector: Collector
    fun <E> withCapture(
        key: String,
        closure: () -> E
    ): E {
        val measured = measureTimedValue {
            runCatching { closure() }
        }
        collector.invoke(
            Monitor.Data(
                key = key,
                durationMillis = measured.duration.inWholeMilliseconds,
                exception = measured.value.exceptionOrNull()
            )
        )
        return measured.value.getOrThrow()
    }

    fun <E> withResultCapture(
        key: String,
        closure: () -> Result<E>
    ): Result<E> {
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
        return measured.value
    }
}