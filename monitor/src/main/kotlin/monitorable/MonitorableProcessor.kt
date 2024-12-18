package monitorable

import com.google.devtools.ksp.getDeclaredFunctions
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
        declaration
            .getDeclaredFunctions()
            .forEach { function ->
                if (function.validate()) {
                    generateMonitoredFunction(function, classBuilder)
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
        val extensionFunVararg = FunSpec.builder("monitored")
            .receiver(declaration.asClassName())
            .returns(declaration.asClassName())
            .addParameter(
                ParameterSpec.builder(
                    "collectors",
                    ClassName("monitorable", "MonitorCollector")
                )
                    .addModifiers(KModifier.VARARG)
                    .build()
            )
            .addCode("""
                return ${proxyClassName}(
                    impl = this,
                    collector = CompositeCollector(*collectors)
                )
            """.trimIndent())
            .build()
        val file = FileSpec.builder(packageName, proxyClassName)
            .addImport("monitorable", "MonitorData")
            .addImport("monitorable", "CompositeCollector")
            .addImport("monitorable", "MonitorCollector")
            .addImport("monitorable", "LoggingCollector")
            .addImport("kotlin.time", "measureTimedValue")
            .addType(classBuilder.build())
            .addFunction(extensionFun)
            .addFunction(extensionFunVararg)
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

    private fun extractMethodName(function: KSFunctionDeclaration): String {
        // Find MonitorMethod annotation if it exists
        val monitorMethodAnnotation = function.annotations.find {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "monitorable.MonitorMethod"
        }

        // Extract custom name if annotation is present, otherwise use function name
        return monitorMethodAnnotation?.arguments?.firstOrNull {
            it.name?.asString() == "name"
        }?.value as? String
            ?: function.simpleName.asString()
    }

    private fun generateMonitoredFunction(
        function: KSFunctionDeclaration,
        classBuilder: TypeSpec.Builder,
    ) {
        val methodName = extractMethodName(function)
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
            |val measured = measureTimedValue { 
            |    impl.${function.simpleName.asString()}($paramNames)
            |}
            |
            |collector.onCollection(
            |    MonitorData(
            |        methodName = "$methodName",
            |        durationMillis = measured.duration.inWholeMilliseconds,
            |        successful = measured.value.isSuccess,
            |        exception = measured.value.exceptionOrNull()
            |    )
            |)
            |
            |return measured.value
            |""".trimMargin())
            } else {
                append("""
            |var exception: Throwable? = null
            |val measured = measureTimedValue { 
            |    kotlin.runCatching {
            |        impl.${function.simpleName.asString()}($paramNames)
            |    }
            |}
            |
            |collector.onCollection(
            |    MonitorData(
            |        methodName = "$methodName",
            |        durationMillis = measured.duration.inWholeMilliseconds,
            |        successful = measured.value.isSuccess,
            |        exception = measured.value.exceptionOrNull()
            |    )
            |)
            |
            |return measured.value.getOrThrow()
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
