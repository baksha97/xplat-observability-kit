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
    var mutating: Int

    val sample: Int?

    val nestedRequired: Nested
    val nestedOptional: Nested?

    @Monitor.Function("get_user")
    fun getUser(id: String): String

    @Monitor.Function("validate_credentials")
    fun validateCredentials(username: String, password: String): Result<Boolean>

    @Monitor.Function("successful_operation")
    fun successfulOperation(input: String): String

    @Monitor.Function("successful_suspend_operation")
    suspend fun successfulSuspendOperation(input: String): String

    fun failingOperation(exception: Exception): String

    suspend fun failingSuspendOperation(exception: Exception): String

    fun resultSucceedingOperation(input: String): Result<String>

    suspend fun resultSucceedingSuspendOperation(input: String): Result<String>

    @Monitor.Function("result_failed_op")
    fun resultFailingOperation(exception: Exception): Result<String>

    @Monitor.Function("result_failed_suspend_op")
    suspend fun resultFailingSuspendOperation(exception: Exception): Result<String>

    @Monitor.Collectable
    interface Nested {
        var mutating: Int
        val sample: Int?
        @Monitor.Function("nested_result_successful_suspend_op")
        suspend fun resultSucceedingSuspendOperation(input: String): Result<String>
    }
}

class UserServiceImpl(
    override val nestedOptional: UserService.Nested,
    override val nestedRequired: UserService.Nested
) : UserService {
    override var mutating: Int = 0
    override val sample: Int?
        get() = 1

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

    override fun successfulOperation(input: String): String {
        return input
    }

    override suspend fun successfulSuspendOperation(input: String): String {
        return input
    }

    override fun failingOperation(exception: Exception): String {
        throw exception
    }

    override suspend fun failingSuspendOperation(exception: Exception): String {
        throw exception
    }

    override fun resultSucceedingOperation(input: String): Result<String> {
        return Result.success(input)
    }

    override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> {
        return Result.success(input)
    }

    override fun resultFailingOperation(exception: Exception): Result<String> {
        return Result.failure(exception)
    }

    override suspend fun resultFailingSuspendOperation(exception: Exception): Result<String> {
        return Result.failure(exception)
    }
}

class TestNested : UserService.Nested {
    override var mutating: Int = 0
    override val sample: Int = 3
    override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> {
        return Result.success(input)
    }
}

private class UserNotFoundException(id: String) : Exception("User not found: $id")
private class ServiceUnavailableException : Exception("Service temporarily unavailable")