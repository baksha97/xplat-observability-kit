package com.baksha.aop.modern.kotlin

import monitorable.Monitoring

@Monitoring
interface UserService {
    fun getUser(): String
    fun getResult(): Result<String>
}

fun main() {
    listOf(
        SucceedingUserServiceImpl()
            .monitored(),
        FailingUserServiceImpl()
            .monitored(),
    ).forEach { service ->
        service.getResult()
        service.getUser()
        println("===")
    }
}

class SucceedingUserServiceImpl : UserService {
    override fun getUser(): String = "user"
    override fun getResult(): Result<String> = runCatching {
        "user"
    }
}

class FailingUserServiceImpl : UserService {
    override fun getUser(): String = throw MyException
    override fun getResult(): Result<String> = runCatching {
        throw MyException
    }
}


object MyException: Exception("ba dum tiss")
