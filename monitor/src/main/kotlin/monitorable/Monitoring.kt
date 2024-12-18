package monitorable

@Retention(AnnotationRetention.SOURCE)
annotation class Monitoring

data class MonitorData(
    val methodName: String,
    val durationMillis: Long,
    val successful: Boolean,
    val exception: Throwable? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

interface MonitorCollector {
    fun onCollection(monitorData: MonitorData)
}

interface Monitorable <T> {
    fun monitored(collector: MonitorCollector = LoggingCollector()): T
}


class CompositeCollector(
    private val collectors: List<MonitorCollector>
) : MonitorCollector {
    constructor(vararg collectors: MonitorCollector) : this(collectors.toList())

    override fun onCollection(monitorData: MonitorData) {
        collectors.forEach { it.onCollection(monitorData) }
    }
}

class LoggingCollector : MonitorCollector {
    override fun onCollection(monitorData: MonitorData) {
        println(monitorData)
    }
}

