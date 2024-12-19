package com.baksha.aop.modern.kotlin

import monitorable.Monitor
import kotlin.random.Random

class UserNotFoundException(id: String) : Exception("User not found: $id")
class ServiceUnavailableException : Exception("Service temporarily unavailable")

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