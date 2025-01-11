// MonitoredMacro.swift


// MonitoredMacro+Implementation.swift

import SwiftCompilerPlugin
import SwiftSyntax
import SwiftSyntaxBuilder
import SwiftSyntaxMacros

public struct MonitoredMacro: MemberMacro, ExtensionMacro {
    public static func expansion(
        of node: AttributeSyntax,
        providingMembersOf declaration: some DeclGroupSyntax,
        in context: some MacroExpansionContext
    ) throws -> [DeclSyntax] {
        // Return empty array since we don't want to add members to the protocol
        return []
    }
    
    public static func expansion(
        of node: AttributeSyntax,
        attachedTo declaration: some DeclGroupSyntax,
        providingExtensionsOf type: some TypeSyntaxProtocol,
        conformingTo protocols: [TypeSyntax],
        in context: some MacroExpansionContext
    ) throws -> [ExtensionDeclSyntax] {
        guard let protocolDecl = declaration.as(ProtocolDeclSyntax.self) else {
            throw CustomError.requiresProtocolType
        }
        
        let protocolName = protocolDecl.name.text
        
        // Generate proxy class
        let proxyClass = try generateProxyClass(protocolDecl: protocolDecl)
        
        // Generate extension
        let monitoredExtension = try generateMonitoredExtension(protocolName: protocolName)
        
        // We need to return both the proxy class and the extension
        // We'll wrap the proxy class in an extension of the module
        let moduleExtension = ExtensionDeclSyntax(
            modifiers: DeclModifierListSyntax([]),
            extendedType: IdentifierTypeSyntax(name: .identifier(protocolName)),
            memberBlock: MemberBlockSyntax(
                leftBrace: .leftBraceToken(),
                members: MemberBlockItemListSyntax([
                    MemberBlockItemSyntax(decl: proxyClass),
                ]),
                rightBrace: .rightBraceToken()
            )
        )
        
        return [moduleExtension, monitoredExtension]
    }
    
    private static func generateProxyClass(protocolDecl: ProtocolDeclSyntax) throws -> DeclSyntax {
        let protocolName = protocolDecl.name.text
        
        let methods = protocolDecl.memberBlock.members.compactMap {
            $0.decl.as(FunctionDeclSyntax.self)
        }
        
        let methodImplementations = methods.map { method in
            let functionParams = method.signature.parameterClause.parameters.map { param in
                let paramName = param.secondName?.text ?? param.firstName.text
                let label = param.firstName.text == "_" ? "_" : param.firstName.text
                return (label: label, name: paramName, type: param.type)
            }
            
            let paramList = functionParams.map { param in
                if param.label == "_" {
                    return "\(param.label) \(param.name): \(param.type)"
                } else {
                    return "\(param.label): \(param.type)"
                }
            }.joined(separator: ", ")
            
            let argumentList = functionParams.map { param in
                if param.label == "_" {
                    return param.name
                } else {
                    return "\(param.label): \(param.name)"
                }
            }.joined(separator: ", ")
            
            let throwsKeyword = method.signature.effectSpecifiers?.throwsSpecifier != nil ? " throws" : ""
            let returnType = method.signature.returnClause?.type.description ?? "Void"
            
            return """
                func \(method.name)\(\(paramList))\(throwsKeyword) -> \(returnType) {
                    try withThrowingCapture(key: "\(method.identifier)") {
                        try underlying.\(method.identifier)(\(argumentList))
                    }
                }
            """
        }.joined(separator: "\n\n")
        
        let proxyClassString = """
        struct \(protocolName)Proxy: \(protocolName), Capturing {
            let underlying: \(protocolName)
            let collector: any Monitor.Collector
            
            init(underlying: \(protocolName), collector: any Monitor.Collector) {
                self.underlying = underlying
                self.collector = collector
            }
            
            \(methodImplementations)
        }
        """
        
        return DeclSyntax(stringLiteral: proxyClassString)
    }
  
  private static func generateMonitoredExtension(protocolName: String) throws -> ExtensionDeclSyntax {
      return ExtensionDeclSyntax(
          extendedType: IdentifierTypeSyntax(name: .identifier(protocolName)),
          memberBlock: MemberBlockSyntax(
              leftBrace: .leftBraceToken(),
              members: MemberBlockItemListSyntax([
                  MemberBlockItemSyntax(
                      decl: FunctionDeclSyntax(
                          modifiers: DeclModifierListSyntax([]),
                          funcKeyword: .keyword(.func),
                          identifier: .identifier("monitored"),
                          signature: FunctionSignatureSyntax(
                              parameterClause: FunctionParameterClauseSyntax(
                                  leftParen: .leftParenToken(),
                                  parameters: FunctionParameterListSyntax([
                                      FunctionParameterSyntax(
                                          firstName: .identifier("collector"),
                                          colon: .colonToken(),
                                          type: IdentifierTypeSyntax(name: .identifier("any Monitor.Collector"))
                                      )
                                  ]),
                                  rightParen: .rightParenToken()
                              ),
                              returnClause: ReturnClauseSyntax(
                                  arrow: .arrowToken(),
                                  type: IdentifierTypeSyntax(name: .identifier(protocolName))
                              )
                          ),
                          body: CodeBlockSyntax(
                              leftBrace: .leftBraceToken(),
                              statements: CodeBlockItemListSyntax([
                                  CodeBlockItemSyntax(
                                      item: .stmt(
                                          StmtSyntax(
                                              ReturnStmtSyntax(
                                                  returnKeyword: .keyword(.return),
                                                  expression: FunctionCallExprSyntax(
                                                      calledExpression: DeclReferenceExprSyntax(
                                                          baseName: .identifier("\(protocolName)Proxy")
                                                      ),
                                                      leftParen: .leftParenToken(),
                                                      arguments: LabeledExprListSyntax([
                                                          LabeledExprSyntax(
                                                              label: .identifier("underlying"),
                                                              colon: .colonToken(),
                                                              expression: DeclReferenceExprSyntax(
                                                                  baseName: .keyword(.`self`)
                                                              )
                                                          ),
                                                          LabeledExprSyntax(
                                                              label: .identifier("collector"),
                                                              colon: .colonToken(),
                                                              expression: DeclReferenceExprSyntax(
                                                                  baseName: .identifier("collector")
                                                              )
                                                          )
                                                      ]),
                                                      rightParen: .rightParenToken()
                                                  )
                                              )
                                          )
                                      )
                                  )
                              ]),
                              rightBrace: .rightBraceToken()
                          )
                      )
                  )
              ]),
              rightBrace: .rightBraceToken()
          )
      )
  }
}

enum CustomError: Error {
    case requiresProtocolType
}

@main
struct MonitoredPlugin: CompilerPlugin {
    let providingMacros: [Macro.Type] = [
        MonitoredMacro.self
    ]
}
