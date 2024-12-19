package monitorable

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.OutputStreamWriter

private const val ANNOTATION_FQN = "monitorable.Monitor.Collectable"
private const val FUNCTION_ANNOTATION_FQN = "monitorable.Monitor.Function"
private const val COLLECTOR_SIMPLE_TYPE = "Monitor.Collector"
private const val DEFAULT_COLLECTOR_SIMPLE_TYPE = "Monitor.Collectors.Printer"
private const val COMPOSITE_COLLECTOR_SIMPLE_TYPE = "Monitor.Collectors.Composite"

class MonitorableProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
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

        // Create the proxy class as private
        val classBuilder = TypeSpec.classBuilder(proxyClassName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(declaration.toClassName())
            .addSuperinterface(ClassName("monitorable", "Capturing"))

        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter("underlying", declaration.toClassName())
            .addParameter(
                "collector",
                ClassName("monitorable", COLLECTOR_SIMPLE_TYPE)
            )

        classBuilder.primaryConstructor(constructorBuilder.build())
            .addProperty(
                PropertySpec.builder("underlying", declaration.toClassName())
                    .initializer("underlying")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "collector",
                    ClassName("monitorable", COLLECTOR_SIMPLE_TYPE)
                )
                    .initializer("collector")
                    .addModifiers(KModifier.OVERRIDE)
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

        // Create extension functions
        val extensionFun = FunSpec.builder("monitored")
            .receiver(declaration.toClassName())
            .returns(declaration.toClassName())
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
                    underlying = this,
                    collector = collector
                )
            """.trimIndent())
            .build()

        val extensionFunVararg = FunSpec.builder("monitored")
            .receiver(declaration.toClassName())
            .returns(declaration.toClassName())
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
                    underlying = this,
                    collector = $COMPOSITE_COLLECTOR_SIMPLE_TYPE(*collectors)
                )
            """.trimIndent())
            .build()

        val file = FileSpec.builder(packageName, proxyClassName)
            .addImport("monitorable", "Monitor")
            .addImport("monitorable", "Capturing")
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
        val monitorMethodAnnotation = function.annotations.find {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == FUNCTION_ANNOTATION_FQN
        }

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
        val implCall = "underlying.${function.simpleName.asString()}($paramNames)"

        val captureMethod =
            if (isResultReturn) "withResultCapture"
            else "withThrowingCapture"
        val code =
            """
            |return $captureMethod("$methodName") {
            |    $implCall
            |}
            """.trimMargin()

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