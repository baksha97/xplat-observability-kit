package com.baksha.observability.core.span

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.OutputStreamWriter

private const val TRACEABLE_ANNOTATION_FQN = "com.baksha.observability.core.span.Traceable"
private const val SPAN_ANNOTATION_FQN = "com.baksha.observability.core.span.Traceable.Span"
private const val SPAN_CAPTURING_SIMPLE_NAME = "SpanCapturing"

class TraceableProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("TraceableProcessor running...")

        // Find all interfaces annotated with @Traceable
        val symbols = resolver.getSymbolsWithAnnotation(TRACEABLE_ANNOTATION_FQN)
        val invalid = symbols.filterNot { it.validate() }

        symbols
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() && it.classKind == ClassKind.INTERFACE }
            .forEach { interfaceDecl ->
                generateSpanningProxy(interfaceDecl)
            }

        return invalid.toList()
    }

    private fun generateSpanningProxy(interfaceDecl: KSClassDeclaration) {
        val packageName = interfaceDecl.packageName.asString()
        val interfaceName = interfaceDecl.simpleName.asString()
        val proxyClassName = "${interfaceName}SpanningProxy"

        val classBuilder = TypeSpec.classBuilder(proxyClassName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(interfaceDecl.toClassName())
            .superclass(ClassName(packageName = "com.baksha.observability.core.span", SPAN_CAPTURING_SIMPLE_NAME))

        // Primary constructor: (underlying: Xyz, tracer: Tracer)
        val ctor = FunSpec.constructorBuilder()
            .addParameter("underlying", interfaceDecl.toClassName())
            .addParameter("tracer", ClassName("io.opentelemetry.api.trace", "Tracer"))
            .build()

        classBuilder.primaryConstructor(ctor)
            .addSuperclassConstructorParameter("tracer")
            .addProperty(
                PropertySpec.builder("underlying", interfaceDecl.toClassName())
                    .initializer("underlying")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

        // Generate method overrides
        interfaceDecl.getDeclaredFunctions()
            .filter { it.validate() }
            .forEach { function -> generateMethodProxy(function, classBuilder) }

        // Generate property overrides
        val nestedProxyProps = mutableListOf<PropertySpec>()
        interfaceDecl.getAllProperties().filter { it.validate() }.forEach { property ->
            val type = property.type.resolve()
            if (isTraceableInterface(type)) {
                nestedProxyProps.add(generateNestedProxy(property, classBuilder))
            } else {
                generatePassthroughProperty(property, classBuilder)
            }
        }
        nestedProxyProps.forEach { classBuilder.addProperty(it) }

        // Extension function: fun MyInterface.traced(tracer: Tracer): MyInterface = ...
        val extFun = FunSpec.builder("traced")
            .receiver(interfaceDecl.toClassName())
            .returns(interfaceDecl.toClassName())
            .addParameter("tracer", ClassName("io.opentelemetry.api.trace", "Tracer"))
            .addCode("return $proxyClassName(this, tracer)")
            .build()

        // Write to a file
        val fileSpec = FileSpec.builder(packageName, proxyClassName)
            .addType(classBuilder.build())
            .addFunction(extFun)
            .build()

        codeGenerator.createNewFile(
            dependencies = Dependencies(false, interfaceDecl.containingFile!!),
            packageName = packageName,
            fileName = proxyClassName
        ).use { output ->
            OutputStreamWriter(output).use { writer ->
                fileSpec.writeTo(writer)
            }
        }
    }

    /**
     * Checks if a given KSType is an interface annotated with @Traceable.
     */
    private fun isTraceableInterface(type: KSType): Boolean {
        val decl = type.declaration as? KSClassDeclaration ?: return false
        if (decl.classKind != ClassKind.INTERFACE) return false
        return decl.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == TRACEABLE_ANNOTATION_FQN
        }
    }

    /**
     * Generate the override for a single interface function, wrapping its call with a span capture.
     */
    private fun generateMethodProxy(function: KSFunctionDeclaration, classBuilder: TypeSpec.Builder) {
        val funName = function.simpleName.asString()

        val methodBuilder = FunSpec.builder(funName)
            .addModifiers(KModifier.OVERRIDE)

        // Add parameters
        function.parameters.forEach { param ->
            val paramName = param.name?.asString().orEmpty()
            val paramType = param.type.resolve().toTypeName()
            methodBuilder.addParameter(paramName, paramType)
        }

        // Determine return type
        val returnType = function.returnType?.resolve()
        if (returnType != null) methodBuilder.returns(returnType.toTypeName())

        // Check if suspend, and if returns kotlin.Result
        val isSuspend = Modifier.SUSPEND in function.modifiers
        if (isSuspend) methodBuilder.addModifiers(KModifier.SUSPEND)

        val returnsResult = (returnType?.declaration?.qualifiedName?.asString() == "kotlin.Result")

        // Extract annotation info from @Traceable.Span
        val (spanName, captureParams, additionalAttrs) = extractSpanData(function)

        // Figure out the correct capturing function name
        val captureFunctionName = when {
            isSuspend && returnsResult -> "withSuspendingSpanCaptureResult"
            isSuspend                  -> "withSuspendingSpanCapture"
            !isSuspend && returnsResult -> "withSpanCaptureResult"
            else                       -> "withSpanCapture"
        }

        // Build the argument list for underlying method
        val paramNames = function.parameters.joinToString(", ") { it.name?.asString().orEmpty() }

        // Decide whether to generate the short form or the long form
        val needsLambdaParam = captureParams.isNotEmpty() || additionalAttrs.isNotEmpty()
        val codeBlock = if (!needsLambdaParam) {
            // No captured attributes -> short form
            """
            return $captureFunctionName("$spanName") {
                underlying.$funName($paramNames)
            }
            """.trimIndent()
        } else {
            // We have attributes to set -> long form with `span ->`
            buildString {
                appendLine("return $captureFunctionName(\"$spanName\") { span ->")
                // For each captureParam, call: span.setAttribute("name", name.toString())
                captureParams.forEach { capturedParam ->
                    appendLine("    span.setAttribute(\"$capturedParam\", $capturedParam.toString())")
                }
                // For each additionalAttr, call: span.setAttribute("attr", underlying.attr.toString())
                additionalAttrs.forEach { attr ->
                    appendLine("    span.setAttribute(\"$attr\", underlying.$attr.toString())")
                }
                appendLine("    underlying.$funName($paramNames)")
                appendLine("}")
            }
        }

        methodBuilder.addCode(codeBlock)
        classBuilder.addFunction(methodBuilder.build())
    }

    /**
     * Extracts the data from an optional @Traceable.Span annotation:
     *   - name (String)
     *   - captureParameters ([String])
     *   - additionalContextFromAttributes ([String])
     *
     * If no annotation is present, we default to (functionName, empty, empty).
     */
    private fun extractSpanData(function: KSFunctionDeclaration): Triple<String, List<String>, List<String>> {
        val annotation = function.annotations.find {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SPAN_ANNOTATION_FQN
        } ?: return Triple(function.simpleName.asString(), emptyList(), emptyList())

        // name
        val spanName = annotation.arguments
            .find { it.name?.asString() == "name" }
            ?.value as? String ?: function.simpleName.asString()

        // captureParameters
        @Suppress("UNCHECKED_CAST")
        val captureParams = (annotation.arguments
            .find { it.name?.asString() == "captureParameters" }
            ?.value as? List<String>) ?: emptyList()

        // additionalContextFromAttributes
        @Suppress("UNCHECKED_CAST")
        val additionalAttrs = (annotation.arguments
            .find { it.name?.asString() == "additionalContextFromAttributes" }
            ?.value as? List<String>) ?: emptyList()

        return Triple(spanName, captureParams, additionalAttrs)
    }

    /**
     * Generates a property override that simply delegates to the underlying instance.
     */
    private fun generatePassthroughProperty(
        property: KSPropertyDeclaration,
        classBuilder: TypeSpec.Builder
    ) {
        val propName = property.simpleName.asString()
        val propType = property.type.resolve().toTypeName()

        val propBuilder = PropertySpec.builder(propName, propType)
            .addModifiers(KModifier.OVERRIDE)
            .getter(
                FunSpec.getterBuilder()
                    .addCode("return underlying.$propName")
                    .build()
            )

        if (property.isMutable) {
            propBuilder.mutable(true)
            propBuilder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", propType)
                    .addCode("underlying.$propName = value")
                    .build()
            )
        }

        classBuilder.addProperty(propBuilder.build())
    }

    /**
     * Generates a nested property override for a @Traceable interface type, wrapping it with .traced(tracer).
     */
    private fun generateNestedProxy(
        property: KSPropertyDeclaration,
        classBuilder: TypeSpec.Builder
    ): PropertySpec {
        val propName = property.simpleName.asString()
        val resolvedType = property.type.resolve()
        val typeName = resolvedType.toTypeName()
        val isNullable = resolvedType.isMarkedNullable

        // We'll create a private backing property: "val nestedRequiredSpanningProxy = underlying.nestedRequired.traced(tracer)"
        val backingPropName = "${propName}SpanningProxy"
        val initExpr = if (isNullable) {
            "underlying.$propName?.traced(tracer)"
        } else {
            "underlying.$propName.traced(tracer)"
        }

        classBuilder.addProperty(
            PropertySpec.builder(backingPropName, typeName)
                .addModifiers(KModifier.PRIVATE)
                .initializer(initExpr)
                .build()
        )

        // Then expose it publicly as override val nestedRequired: ...
        return PropertySpec.builder(propName, typeName)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder().addCode("return $backingPropName").build())
            .build()
    }
}

class TraceableProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return TraceableProcessor(environment.codeGenerator, environment.logger)
    }
}
