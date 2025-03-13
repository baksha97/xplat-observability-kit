package com.baksha.observability.core

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.OutputStreamWriter

private const val ANNOTATION_FQN = "com.baksha.observability.core.Monitor.Collectable"
private const val FUNCTION_ANNOTATION_FQN = "com.baksha.observability.core.Monitor.Function"
private const val COLLECTOR_SIMPLE_TYPE = "Monitor.Collector"
private const val DEFAULT_COLLECTOR_SIMPLE_TYPE = "Monitor.Collectors.Printer"
private const val COMPOSITE_COLLECTOR_SIMPLE_TYPE = "Monitor.Collectors.Composite"

private const val PACKAGE = "com.baksha.observability.core"

internal class MonitorableProcessor(
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

    private fun isCollectableInterface(type: KSType): Boolean {
        val declaration = type.declaration
        if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE) {
            return declaration.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_FQN
            }
        }
        return false
    }

    private fun processInterface(declaration: KSClassDeclaration) {
        val packageName = declaration.packageName.asString()
        val interfaceName = declaration.simpleName.asString()
        val proxyClassName = "${interfaceName}MonitoringProxy"

        // Create the proxy class as private with superclass constructor call
        val classBuilder = TypeSpec.classBuilder(proxyClassName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(declaration.toClassName())
            .superclass(ClassName(PACKAGE, "Capturing"))

        // Create constructor that directly calls super constructor with collector
        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter(
                ParameterSpec.builder("underlying", declaration.toClassName())
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("collector", ClassName(PACKAGE, COLLECTOR_SIMPLE_TYPE))
                    .build()
            )

        // Create the class with the constructor parameters
        classBuilder
            .primaryConstructor(constructorBuilder.build())
            .addSuperclassConstructorParameter("collector")
            .addProperty(
                PropertySpec.builder("underlying", declaration.toClassName())
                    .initializer("underlying")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

        // Handle functions
        declaration
            .getDeclaredFunctions()
            .filter { it.validate() }
            .forEach { function ->
                generateMonitoredFunction(function, classBuilder)
            }

        // Handle properties - both regular and nested interfaces
        val nestedProxies = mutableListOf<PropertySpec>()
        declaration.getAllProperties().filter { it.validate() }.forEach { property ->
            val propertyType = property.type.resolve()
            if (isCollectableInterface(propertyType)) {
                val nestedProxyProperty = generateNestedProxy(property, classBuilder)
                nestedProxies.add(nestedProxyProperty)
            } else {
                generatePropertyPassthrough(property, classBuilder)
            }
        }

        // Add nested proxies
        nestedProxies.forEach { proxyProp ->
            classBuilder.addProperty(proxyProp)
        }

        // Extension functions
        val extensionFun = createExtensionFunction(declaration.toClassName(), proxyClassName)
        val extensionFunVararg = createExtensionVarargFunction(declaration.toClassName(), proxyClassName)

        val file = FileSpec.builder(packageName, proxyClassName)
            .addImport(PACKAGE, "Monitor")
            .addImport(PACKAGE, "Capturing")
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

    private fun generatePropertyPassthrough(
        property: KSPropertyDeclaration,
        classBuilder: TypeSpec.Builder
    ) {
        val propertyBuilder = PropertySpec.builder(
            property.simpleName.asString(),
            property.type.resolve().toTypeName()
        )
            .addModifiers(KModifier.OVERRIDE)

        // Add getter
        propertyBuilder.getter(
            FunSpec.getterBuilder()
                .addCode("return underlying.${property.simpleName.asString()}")
                .build()
        )

        // Add setter if property is mutable
        if (property.isMutable) {
            propertyBuilder.mutable(true)
            propertyBuilder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", property.type.resolve().toTypeName())
                    .addCode("underlying.${property.simpleName.asString()} = value")
                    .build()
            )
        }

        classBuilder.addProperty(propertyBuilder.build())
    }

    private fun generateNestedProxy(
        property: KSPropertyDeclaration,
        classBuilder: TypeSpec.Builder,
    ): PropertySpec {
        val propertyName = property.simpleName.asString()
        val propertyType = property.type.resolve().toTypeName()
        val isNullable = property.type.resolve().isMarkedNullable

        val nestedProxyProperty = PropertySpec.builder(propertyName, propertyType)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("${propertyName}Proxy")
            .build()

        val backingProperty = PropertySpec.builder("${propertyName}Proxy", propertyType)
            .addModifiers(KModifier.PRIVATE)
            .initializer(if (isNullable) {
                "underlying.$propertyName?.monitored(collector)"
            } else {
                "underlying.$propertyName.monitored(collector)"
            })
            .build()

        classBuilder.addProperty(backingProperty)

        return nestedProxyProperty
    }

    private fun createExtensionFunction(interfaceClassName: ClassName, proxyClassName: String): FunSpec {
        return FunSpec.builder("monitored")
            .receiver(interfaceClassName)
            .returns(interfaceClassName)
            .addParameter(
                ParameterSpec.builder("collector", ClassName(PACKAGE, COLLECTOR_SIMPLE_TYPE))
                    .defaultValue("$DEFAULT_COLLECTOR_SIMPLE_TYPE()")
                    .build()
            )
            .addCode(
                """
                return $proxyClassName(
                    underlying = this,
                    collector = collector
                )
                """.trimIndent()
            )
            .build()
    }

    private fun createExtensionVarargFunction(interfaceClassName: ClassName, proxyClassName: String): FunSpec {
        return FunSpec.builder("monitored")
            .receiver(interfaceClassName)
            .returns(interfaceClassName)
            .addParameter(
                ParameterSpec.builder(
                    "collectors",
                    ClassName(PACKAGE, COLLECTOR_SIMPLE_TYPE)
                )
                    .addModifiers(KModifier.VARARG)
                    .build()
            )
            .addCode(
                """
                return $proxyClassName(
                    underlying = this,
                    collector = $COMPOSITE_COLLECTOR_SIMPLE_TYPE(*collectors)
                )
                """.trimIndent()
            )
            .build()
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

        if (function.modifiers.contains(Modifier.SUSPEND)) {
            methodBuilder.addModifiers(KModifier.SUSPEND)
        }

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

        val captureMethod = if (isResultReturn) "withResultCapture" else "withThrowingCapture"

        val code = """
            |return $captureMethod("$methodName") {
            |    $implCall
            |}
            """.trimMargin()

        methodBuilder.addCode(code)
        classBuilder.addFunction(methodBuilder.build())
    }
}

internal class MonitorableProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return MonitorableProcessor(environment.codeGenerator, environment.logger)
    }
}