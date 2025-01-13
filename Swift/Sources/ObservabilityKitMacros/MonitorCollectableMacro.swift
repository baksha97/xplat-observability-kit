//// MonitorCollectableMacro.swift
//
//import SwiftCompilerPlugin
//import SwiftSyntax
//import SwiftSyntaxBuilder
//import SwiftSyntaxMacros
//
//
//public struct MonitorCollectableMacro: ConformanceMacro, MemberAttributeMacro {
//    /// 1) Add conformance to `Capturing`
//    public static func expansion(
//        of node: AttributeSyntax,
//        providingConformancesOf declaration: some DeclGroupSyntax,
//        in context: some MacroExpansionContext
//    ) throws -> [TypeIdentifierSyntax] {
//        // Always add Capturing conformance
//        return [TypeIdentifierSyntax(name: .identifier("Capturing"))]
//    }
//
//    /// 2) For each function not marked `@CaptureIgnored`, add `@Captured("funcName")`
//    public static func expansion(
//        of node: AttributeSyntax,
//        providingAttributesFor member: DeclSyntax,
//        in context: some MacroExpansionContext
//    ) throws -> [AttributeSyntax] {
//        guard let funcDecl = member.as(FunctionDeclSyntax.self) else {
//            return []
//        }
//
//        // Check for @CaptureIgnored
//        let isIgnored = funcDecl.attributes?.contains(where: { attr in
//            attr.as(AttributeSyntax.self)?.attributeName.as(IdentifierTypeSyntax.self)?.name.text == "CaptureIgnored"
//        }) ?? false
//
//        if isIgnored {
//            return []
//        }
//
//        // Build `@Captured("functionName")`
//        let functionName = funcDecl.identifier.text
//        let attrText = "@Captured(\"\(functionName)\")"
//
//        // Parse the string into an AttributeSyntax
//        let parsedSyntax = try SyntaxParser.parse(source: attrText)
//        guard let item = parsedSyntax.statements.first?.item.as(AttributeSyntax.self) else {
//            return []
//        }
//
//        return [item]
//    }
//}
//#endif
