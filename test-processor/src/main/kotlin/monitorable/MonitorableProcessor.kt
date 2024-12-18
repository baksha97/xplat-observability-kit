package monitorable

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.OutputStreamWriter

class MonitorableProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    private fun KSClassDeclaration.asClassName(): ClassName {
        return ClassName(
            packageName = this.packageName.asString(),
            simpleNames = this.qualifiedName?.asString()?.split(".")?.drop(
                this.packageName.asString().split(".").size
            ) ?: listOf(this.simpleName.asString())
        )
    }

    private fun KSType.toTypeName(): TypeName {
        val rawType = when (val declaration = this.declaration) {
            is KSClassDeclaration -> declaration.asClassName()
            else -> throw IllegalArgumentException("Unexpected declaration: $declaration")
        }

        if (arguments.isEmpty()) {
            return rawType.copy(nullable = this.nullability == Nullability.NULLABLE)
        }

        val typeArguments = arguments.map { it.type?.resolve()?.toTypeName() ?: ANY }
        return rawType.parameterizedBy(typeArguments).copy(nullable = this.nullability == Nullability.NULLABLE)
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("MonitorableProcessor running")

        val symbols = resolver.getSymbolsWithAnnotation("monitorable.Monitoring")
        logger.info("Found ${symbols.count()} symbols with @Monitoring")

        val ret = symbols.filter { !it.validate() }.toList()

        symbols.filter {
            it is KSClassDeclaration && it.validate() && it.classKind == ClassKind.INTERFACE
        }.forEach {
            logger.info("Processing interface: $it")
            processInterface(it as KSClassDeclaration)
        }

        return ret
    }

    private fun isResultType(type: KSType): Boolean {
        return type.declaration.qualifiedName?.asString() == "kotlin.Result"
    }

    private fun processInterface(declaration: KSClassDeclaration) {
        val packageName = declaration.packageName.asString()
        val interfaceName = declaration.simpleName.asString()
        val proxyClassName = "${interfaceName}MonitoringProxy"

        val monitorableAnnotation = declaration.annotations.find {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "monitorable.Monitorable"
        }

        val config = monitorableAnnotation?.let { extractMonitorConfig(it) }
            ?: MonitorConfig(
                captureResult = false,
                captureExceptions = true
            )

        // Create the proxy class as private
        val classBuilder = TypeSpec.classBuilder(proxyClassName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(declaration.asClassName())

        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter("impl", declaration.asClassName())
            .addParameter(
                "collector",
                ClassName("monitorable", "MonitorCollector")
            )

        classBuilder.primaryConstructor(constructorBuilder.build())
            .addProperty(
                PropertySpec.builder("impl", declaration.asClassName())
                    .initializer("impl")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "collector",
                    ClassName("monitorable", "MonitorCollector")
                )
                    .initializer("collector")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

        // Generate monitored functions
        val builtIns = setOf(
            "equals",
            "hashCode",
            "toString"
        )
        declaration
            .getAllFunctions()
            .filter { !builtIns.contains(it.simpleName.asString()) }
            .forEach { function ->
                if (function.validate()) {
                    generateMonitoredFunction(function, classBuilder, config)
                }
            }

        // Create extension function
        val extensionFun = FunSpec.builder("monitored")
            .receiver(declaration.asClassName())
            .returns(declaration.asClassName())
            .addParameter(
                ParameterSpec.builder(
                    "collector",
                    ClassName("monitorable", "MonitorCollector")
                )
                    .defaultValue("LoggingCollector()")
                    .build()
            )
            .addCode("""
                return ${proxyClassName}(
                    impl = this,
                    collector = collector
                )
            """.trimIndent())
            .build()

        val file = FileSpec.builder(packageName, proxyClassName)
            .addImport("monitorable", "MonitorData")
            .addImport("monitorable", "MonitorCollector")
            .addImport("monitorable", "LoggingCollector")
            .addType(classBuilder.build())
            .addFunction(extensionFun)
            .build()

        codeGenerator.createNewFile(
            dependencies = Dependencies(false, declaration.containingFile!!),
            packageName = packageName,
            fileName = proxyClassName
        ).use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                file.writeTo(writer)
            }
        }
    }

    private data class MonitorConfig(
        val captureResult: Boolean,
        val captureExceptions: Boolean
    )

    private fun extractMonitorConfig(annotation: KSAnnotation): MonitorConfig {
        return MonitorConfig(
            captureResult = annotation.arguments.find { it.name?.asString() == "captureResult" }?.value as? Boolean ?: false,
            captureExceptions = annotation.arguments.find { it.name?.asString() == "captureExceptions" }?.value as? Boolean ?: true
        )
    }

    private fun generateMonitoredFunction(
        function: KSFunctionDeclaration,
        classBuilder: TypeSpec.Builder,
        config: MonitorConfig
    ) {
        val methodBuilder = FunSpec.builder(function.simpleName.asString())
            .addModifiers(KModifier.OVERRIDE)

        function.parameters.forEach { param ->
            methodBuilder.addParameter(
                param.name?.asString() ?: "_",
                param.type.resolve().toTypeName()
            )
        }

        val returnType = function.returnType?.resolve()
        val isResultReturn = returnType?.let { isResultType(it) } ?: false
        returnType?.let { methodBuilder.returns(it.toTypeName()) }

        val paramNames = function.parameters.joinToString(", ") { it.name?.asString() ?: "_" }

        val code = buildString {
            if (isResultReturn) {
                append("""
                |val startTime = System.currentTimeMillis()
                |val result = impl.${function.simpleName.asString()}($paramNames)
                |val duration = System.currentTimeMillis() - startTime
                |
                |collector.onCollection(
                |    MonitorData(
                |        methodName = "${function.simpleName.asString()}",
                |        durationMillis = duration,
                |        successful = result.isSuccess,
                |        exception = result.exceptionOrNull()
                |    )
                |)
                |
                |return result
                |""".trimMargin())
            } else {
                append("""
                |val startTime = System.currentTimeMillis()
                |var success = false
                |var exception: Throwable? = null
                |var result: ${returnType?.toTypeName() ?: UNIT}? = null
                |
                |try {
                |    result = impl.${function.simpleName.asString()}($paramNames)
                |    success = true
                |    return result
                |} catch (e: Throwable) {
                |    exception = e
                |    throw e
                |} finally {
                |    val duration = System.currentTimeMillis() - startTime
                |    collector.onCollection(
                |        MonitorData(
                |            methodName = "${function.simpleName.asString()}",
                |            durationMillis = duration,
                |            successful = success,
                |            exception = ${if (config.captureExceptions) "exception" else "null"}
                |        )
                |    )
                |}
                |""".trimMargin())
            }
        }

        methodBuilder.addCode(code)
        classBuilder.addFunction(methodBuilder.build())
    }
}

class MonitorableProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MonitorableProcessor(environment.codeGenerator, environment.logger)
    }
}

private fun KSClassDeclaration.asClassName(): ClassName {
    return ClassName(
        packageName = this.packageName.asString(),
        simpleNames = this.qualifiedName?.asString()?.split(".")?.drop(
            this.packageName.asString().split(".").size
        ) ?: listOf(this.simpleName.asString())
    )
}

private fun KSType.toTypeName(): TypeName {
    val declaration = this.declaration
    val rawType = when (declaration) {
        is KSClassDeclaration -> declaration.asClassName()
        else -> throw IllegalArgumentException("Unexpected declaration: $declaration")
    }

    if (arguments.isEmpty()) {
        return rawType.copy(nullable = this.nullability == Nullability.NULLABLE)
    }

    val typeArguments = arguments.map { it.type?.resolve()?.toTypeName() ?: ANY }
    return rawType.parameterizedBy(typeArguments).copy(nullable = this.nullability == Nullability.NULLABLE)
}

private fun KSTypeReference.toTypeName(): TypeName {
    return this.resolve().toTypeName()
}
