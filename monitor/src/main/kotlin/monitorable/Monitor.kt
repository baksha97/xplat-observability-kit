package monitorable

object Monitor {
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Collectable
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Function(val name: String)

    data class Data(
        val key: String,
        val durationMillis: Long,
        val exception: Throwable? = null,
    )

    fun interface Collector {
        fun invoke(data: Data)
    }

    object Collectors {
        class Printer: Collector {
            override fun invoke(data: Data) {
                println(data)
                data.exception?.printStackTrace()
            }
        }

        class Composite(private vararg var collectors: Collector) : Collector {
            override fun invoke(data: Data) {
                collectors.forEach { it.invoke(data) }
            }
        }
    }
}
