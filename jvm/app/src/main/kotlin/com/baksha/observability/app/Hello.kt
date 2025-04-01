package com.baksha.observability.app

import com.baksha.observability.core.collect.Collector
import com.baksha.observability.core.collect.internal.ProxyEventCollecting

interface MonitorScope<T> {
    fun T.monitored(collector: Collector): T
}

fun main() {
    val implementation = Implementation()
//    Overload resolution ambiguity. All these functions match.
//    public fun AnotherInterface.monitored(vararg collectors: Collector): AnotherInterface defined in com.baksha.observability.app in file Hello.kt
//    public fun MyInterface.monitored(vararg collectors: Collector): MyInterface defined in com.baksha.observability.app in file Hello.kt
        .monitored()
}

interface MyInterface {
    var mutating: Int
    val sample: Int?
    suspend fun resultSucceedingSuspendOperation(input: String): Result<String>
}

class MyInterfaceProxy(
    private val underlying: MyInterface,
    collector: Collector,
) : ProxyEventCollecting(collector),
    MyInterface {
    override var mutating: Int
        get() = underlying.mutating
        set(`value`) {
            underlying.mutating = value
        }

    override val sample: Int?
        get() = underlying.sample

    override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> = withResultCapture("resultSucceedingSuspendOperation") {
        underlying.resultSucceedingSuspendOperation(input)
    }
}

fun MyInterface.monitored(vararg collectors: Collector): MyInterface = MyInterfaceProxy(
    underlying = this,
    collector = Collector.composite(*collectors)
)


interface AnotherInterface {
    suspend fun resultSucceedingSuspendOperation2(input: String): Result<String>
}

class AnotherInterfaceProxy(
    private val underlying: AnotherInterface,
    collector: Collector,
) : ProxyEventCollecting(collector),
    AnotherInterface {

    override suspend fun resultSucceedingSuspendOperation2(input: String): Result<String> = withResultCapture("resultSucceedingSuspendOperation") {
        underlying.resultSucceedingSuspendOperation2(input)
    }
}

fun AnotherInterface.monitored(vararg collectors: Collector): AnotherInterface = AnotherInterfaceProxy(
    underlying = this,
    collector = Collector.composite(*collectors)
)

interface CompositeInterface : MyInterface, AnotherInterface
class CompositeInterfaceProxy(
    private val underlying: CompositeInterface,
    collector: Collector,
) : ProxyEventCollecting(collector),
    CompositeInterface {
    override var mutating: Int
        get() = underlying.mutating
        set(`value`) {
            underlying.mutating = value
        }

    override val sample: Int?
        get() = underlying.sample

    override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> = withResultCapture("resultSucceedingSuspendOperation") {
        underlying.resultSucceedingSuspendOperation(input)
    }

    override suspend fun resultSucceedingSuspendOperation2(input: String): Result<String> = withResultCapture("resultSucceedingSuspendOperation") {
        underlying.resultSucceedingSuspendOperation2(input)
    }
}

fun CompositeInterface.monitored(vararg collectors: Collector): CompositeInterface = CompositeInterfaceProxy(
    underlying = this,
    collector = Collector.composite(*collectors)
)

open class Implementation : CompositeInterface {
    override var mutating: Int = 0
    override val sample: Int? = null

    override suspend fun resultSucceedingSuspendOperation(input: String): Result<String> {
        return Result.success("Hello")
    }

    override suspend fun resultSucceedingSuspendOperation2(input: String): Result<String> {
        return Result.success("Hello")
    }
}
