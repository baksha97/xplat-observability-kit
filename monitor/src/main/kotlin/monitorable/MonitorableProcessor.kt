package monitorable

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.OutputStreamWriter

private const val ANNOTATION_FQN = "monitorable.Monitor.Collectable"
private const val FUNCTION_ANNOTATION_FQN = "Monitor.Function"
private const val COLLECTOR_SIMPLE_TYPE = "Monitor.Collector"
private const val DATA_SIMPLE_TYPE = "Monitor.Data"
private const val DEFAULT_COLLECTOR_SIMPLE_TYPE = "monitorable.Monitor.Collector.Printer"
private const val COMPOSITE_COLLECTOR_SIMPLE_TYPE = "monitorable.Monitor.Collector.Composite"

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

        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_FQN)
        logger.info("Found ${symbols.count()} symbols with @$ANNOTATION_FQN")

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
            it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_FQN
        }

        // Create the proxy class as private
        val classBuilder = TypeSpec.classBuilder(proxyClassName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(declaration.asClassName())

        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter("impl", declaration.asClassName())
            .addParameter(
                "collector",
                ClassName("monitorable", COLLECTOR_SIMPLE_TYPE)
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
                    ClassName("monitorable", COLLECTOR_SIMPLE_TYPE)
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
                    ClassName("monitorable", COLLECTOR_SIMPLE_TYPE)
                )
                    .defaultValue("$DEFAULT_COLLECTOR_SIMPLE_TYPE()")
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
                    ClassName("monitorable", COLLECTOR_SIMPLE_TYPE)
                )
                    .addModifiers(KModifier.VARARG)
                    .build()
            )
            .addCode("""
                return ${proxyClassName}(
                    impl = this,
                    collector = $COMPOSITE_COLLECTOR_SIMPLE_TYPE(*collectors)
                )
            """.trimIndent())
            .build()
        val file = FileSpec.builder(packageName, proxyClassName)
            .addImport("monitorable", "Monitor")
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
            it.annotationType.resolve().declaration.qualifiedName?.asString() == FUNCTION_ANNOTATION_FQN
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



        fun generateImplCall() = "impl.${function.simpleName.asString()}($paramNames)"
        fun generateMeasuredVal() =
            if (isResultReturn) "val measured = measureTimedValue { ${generateImplCall()} }"
            else "val measured = measureTimedValue { runCatching { ${generateImplCall()} } }"
        fun generateOnCollectionCode() = """
            collector.invoke(
            |    $DATA_SIMPLE_TYPE(
            |        methodName = "$methodName",
            |        durationMillis = measured.duration.inWholeMilliseconds,
            |        exception = measured.value.exceptionOrNull()
            |    )
            |)""".trimIndent()
        fun generateReturn() =
            if (isResultReturn) "return measured.value"
            else "return measured.value.getOrThrow()"
        val code = """
                    |${generateMeasuredVal()}
                    |${generateOnCollectionCode()}
                    |${generateReturn()}
                    |"""
            .trimMargin()

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
