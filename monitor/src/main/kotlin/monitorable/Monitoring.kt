package monitorable

object Monitor {
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Collectable
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Function(val name: String)

    data class Data(
        val methodName: String,
        val durationMillis: Long,
        val exception: Throwable? = null,
    )

    fun interface Collector {
        fun invoke(monitorData: Data)

        class Printer: Collector {
            override fun invoke(monitorData: Data) {
                println(monitorData)
            }
        }

        class Composite(private vararg var collectors: Collector) : Collector {
            override fun invoke(monitorData: Data) {
                collectors.forEach { it.invoke(monitorData) }
            }
        }
    }
}