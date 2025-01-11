package com.baksha.observability.app
import com.baksha.observability.core.Monitor
import kotlin.random.Random


/**
 * The following annotation will generate this proxy class and extension function.
 * ```
 * package com.baksha.observability.app
 *
 * import com.baksha.observability.core.Capturing
 * import com.baksha.observability.core.Monitor
 * import com.baksha.observability.core.Monitor.Collector
 * import kotlin.Boolean
 * import kotlin.Result
 * import kotlin.String
 *
 * private class UserServiceMonitoringProxy(
 *   private val underlying: UserService,
 *   override val collector: Monitor.Collector,
 * ) : UserService,
 *     Capturing {
 *   override fun getUser(id: String): String = withThrowingCapture("get_user") {
 *       underlying.getUser(id)
 *   }
 *
 *   override fun validateCredentials(username: String, password: String): Result<Boolean> = withResultCapture("validate_credentials") {
 *       underlying.validateCredentials(username, password)
 *   }
 * }
 *
 * public fun UserService.monitored(collector: Monitor.Collector = Monitor.Collectors.Printer()): UserService = UserServiceMonitoringProxy(
 *     underlying = this,
 *     collector = collector
 * )
 *
 * public fun UserService.monitored(vararg collectors: Monitor.Collector): UserService = UserServiceMonitoringProxy(
 *     underlying = this,
 *     collector = Monitor.Collectors.Composite(*collectors)
 * )
 *```
 */
@Monitor.Collectable
interface UserService {
    @Monitor.Function(name = "get_user")
    fun getUser(id: String): String

    @Monitor.Function(name = "validate_credentials")
    fun validateCredentials(username: String, password: String): Result<Boolean>
}

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


private class UserNotFoundException(id: String) : Exception("User not found: $id")
private class ServiceUnavailableException : Exception("Service temporarily unavailable")