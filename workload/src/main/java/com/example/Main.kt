package com.example

import monitorable.LoggingCollector


fun main() {
    val service = UserServiceImpl()
        .monitored(collector = LoggingCollector())

    service.getResult()
    println("===")
    service.getUser()
}


