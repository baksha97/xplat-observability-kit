import SwiftCompilerPlugin
import SwiftSyntax
import SwiftSyntaxBuilder
import SwiftSyntaxMacros

public struct CaptureKeyMacro: PeerMacro, AttachedMacro {
    public static func expansion(
        of node: AttributeSyntax,
        providingPeersOf declaration: some DeclSyntaxProtocol,
        in context: some MacroExpansionContext
    ) throws -> [DeclSyntax] {
        // This macro doesn't generate any peer declarations
        return []
    }
  
  public static func expansion(
      of node: AttributeSyntax,
      providingAttributesFor declaration: some DeclSyntaxProtocol,
      in context: some MacroExpansionContext
  ) throws -> [AttributeSyntax] {
      // Ensure we have an argument
    guard let arguments = node.arguments?.as(LabeledExprListSyntax.self),
            let firstArg = arguments.first?.expression,
            firstArg.is(StringLiteralExprSyntax.self) else {
          throw MacroExpansionErrorMessage("@CaptureKey requires a string literal argument")
      }
      
      return []
  }
}
