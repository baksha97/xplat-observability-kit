package com.example

import monitorable.Monitoring

@Monitoring
interface UserService {
    fun getUser(): String
    fun getResult(): Result<String>
}

data object MyException: Exception("This was a failure") {
    private fun readResolve(): Any = MyException
}

class UserServiceImpl : UserService {
    override fun getUser(): String = throw MyException
    override fun getResult(): Result<String> = runCatching {
        throw MyException
    }
}


//fun UserService.monitored(
//    collector: MonitorCollector = LoggingCollector()
//) = UserServiceMonitoringProxy(
//    impl = this,
//    collector = collector
//)
