package com.baksha.aop.modern.kotlin

import monitorable.Monitor

@Monitor.Collectable
interface AuthService {
    @Monitor.Function(name = "auth_user_get")
    fun getUser(): String
    @Monitor.Function(name = "auth_result_get")
    fun getResult(): Result<String>
}

fun main() {
    listOf(
        SucceedingAuthServiceImpl()
                ,
//            .monitored(),
        FailingAuthServiceImpl()
//            .monitored(),
    ).forEach { service ->
        runCatching {
            service.getResult()
            service.getUser()
        }
        println("===")
    }
}

class SucceedingAuthServiceImpl : AuthService {
    override fun getUser(): String = "user"
    override fun getResult(): Result<String> = runCatching {
        "user"
    }
}

class FailingAuthServiceImpl : AuthService {
    override fun getUser(): String = throw MyException
    override fun getResult(): Result<String> = runCatching {
        throw MyException
    }
}


object MyException: Exception("ba dum tiss")
