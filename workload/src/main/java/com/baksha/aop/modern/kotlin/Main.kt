package demo

import monitorable.Monitor
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Monitor.Collectable
interface UserService {
    @Monitor.Function(name = "get_user")
    fun getUser(id: String): String

    @Monitor.Function(name = "validate_credentials")
    fun validateCredentials(username: String, password: String): Result<Boolean>
}

// Sample implementation with simulated delays and failures
class UserServiceImpl : UserService {
    override fun getUser(id: String): String {
        Thread.sleep(Random.nextLong(100, 500)) // Simulate work
        if (Random.nextDouble() < 0.3) {
            throw UserNotFoundException(id)
        }
        return "User#$id"
    }

    override fun validateCredentials(username: String, password: String): Result<Boolean> {
        return runCatching {
            Thread.sleep(Random.nextLong(200, 800)) // Simulate work
            if (Random.nextDouble() < 0.2) {
                throw ServiceUnavailableException()
            }
            username == password
        }
    }
}

class UserNotFoundException(id: String) : Exception("User not found: $id")
class ServiceUnavailableException : Exception("Service temporarily unavailable")

// Custom collector that tracks performance metrics
class MetricsCollector : Monitor.Collector {
    private val metrics = mutableMapOf<String, MutableList<Long>>()
    private val errors = mutableMapOf<String, MutableList<Throwable>>()

    override fun invoke(data: Monitor.Data) {
        metrics.getOrPut(data.key) { mutableListOf() }
            .add(data.durationMillis)

        data.exception?.let { error ->
            errors.getOrPut(data.key) { mutableListOf() }
                .add(error)
        }
    }

    fun printReport() {
        println("\nPerformance Report")
        println("=================")
        metrics.forEach { (key, durations) ->
            val avgDuration = durations.average().milliseconds
            val errorRate = (errors[key]?.size ?: 0) * 100.0 / durations.size

            println("""
                Operation: $key
                Average Duration: $avgDuration
                Error Rate: $errorRate%
                Total Calls: ${durations.size}
                Error Types: ${errors[key]?.groupBy { it::class.simpleName }?.mapValues { it.value.size } ?: "none"}
                """.trimIndent())
            println("---")
        }
    }
}

fun main() {
    // Create service with multiple collectors
    val metricsCollector = MetricsCollector()
    val service = UserServiceImpl()
        .monitored(metricsCollector)
        .monitored(Monitor.Collectors.Printer())

    // Simulate a series of operations
    repeat(5) { iteration ->
        println("\nIteration ${iteration + 1}")
        println("===========")

        // Test getUser
        runCatching {
            val userId = "user_${Random.nextInt(1000)}"
            println("Getting user $userId: ${service.getUser(userId)}")
        }.onFailure { println("Failed to get user: ${it.message}") }

        // Test validateCredentials
        service.validateCredentials("testuser", "secret")
            .onSuccess { valid -> println("Credentials validation: $valid") }
            .onFailure { println("Validation failed: ${it.message}") }

        Thread.sleep(1.seconds.inWholeMilliseconds)
    }

    // Print metrics report
    metricsCollector.printReport()
}