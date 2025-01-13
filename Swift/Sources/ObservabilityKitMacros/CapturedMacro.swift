import SwiftCompilerPlugin
import SwiftSyntax
import SwiftSyntaxBuilder
import SwiftSyntaxMacros

/// The user writes:
///
/// @Captured              // <-- no longer takes a string
/// @CaptureKey("override")
/// func foo() { ... }
///
/// If @CaptureKey(...) is missing, we'll use `foo` as the capture key.
///
public struct CapturedMacro: BodyMacro {
  
  public static func expansion(
    of node: AttributeSyntax,
    providingBodyFor declaration: some DeclSyntaxProtocol & WithOptionalCodeBlockSyntax,
    in context: some MacroExpansionContext
  ) throws -> [CodeBlockItemSyntax] {
    // Ensure this is a function that actually has a body
    guard let function = declaration.as(FunctionDeclSyntax.self),
          let body = function.body
    else {
      throw MacroExpansionErrorMessage("expected a function with a body")
    }
    
    // 1) Find a @CaptureKey(...) attribute on the function and parse its argument if present
    let captureKeyExpr = try findCaptureKeyAttribute(in: function)
    
    // 2) If @CaptureKey is not present, we'll default to the function name
    let functionNameExpr = ExprSyntax(StringLiteralExprSyntax(content: function.name.text))
    
    // The final "key" expression used in `withCapture(...)`
    let keyExpr = captureKeyExpr ?? functionNameExpr
    
    // 3) Construct the `withCapture(...)` function call.
    var withCaptureCall = FunctionCallExprSyntax("withCapture()" as ExprSyntax)!
    withCaptureCall.arguments.append(LabeledExprSyntax(expression: keyExpr))
    
    // 4) Prepare the closure signature by collecting effect specifiers (async/throws/etc.)
    let asyncClause = function.signature.effectSpecifiers?.asyncSpecifier
    let returnClause = function.signature.returnClause
    var throwsClause = function.signature.effectSpecifiers?.throwsClause
    
    // We can't apply "rethrows" to the closure, so degrade rethrows -> throws
    if throwsClause?.throwsSpecifier.tokenKind == .keyword(.rethrows) {
      throwsClause?.throwsSpecifier = .keyword(.throws)
    }
    
    // 5) Build the final expression: `withCapture(key) { ... }`
    var withCaptureExpr: ExprSyntax =
    """
    \(withCaptureCall) {
        \(body.statements)
    }
    """
    
    // 6) Apply the necessary `await` / `try` if the function is async or throwing
    if asyncClause != nil {
      withCaptureExpr = "await \(withCaptureExpr)"
    }
    if throwsClause != nil {
      withCaptureExpr = "try \(withCaptureExpr)"
    }
    
    // Return a single statement as the new function body
    return [
      "\(withCaptureExpr)"
    ]
  }
}

// MARK: - Helper: findCaptureKeyAttribute
//
// Looks for the presence of an attribute named @CaptureKey("someOverride")
// on the given FunctionDeclSyntax. If found, returns its string literal argument
// as an ExprSyntax. Otherwise returns nil.
private func findCaptureKeyAttribute(in function: FunctionDeclSyntax) throws -> ExprSyntax? {
    // Find the @CaptureKey attribute if it exists
    guard let captureKeyAttr = function.attributes.first(where: { attr in
        guard case .attribute(let attr) = attr,
              let identifierExpr = attr.attributeName.as(IdentifierTypeSyntax.self) else {
            return false
        }
        return identifierExpr.name.text == "CaptureKey"
    }) else {
        return nil
    }
    
    // Extract the attribute's argument list
    guard case .attribute(let attr) = captureKeyAttr,
          let arguments = attr.arguments?.as(LabeledExprListSyntax.self),
          let firstArg = arguments.first?.expression,
          let stringLiteral = firstArg.as(StringLiteralExprSyntax.self) else {
        throw MacroExpansionErrorMessage("@CaptureKey expects a string literal argument")
    }
    
    // Return the string literal as an ExprSyntax
    return ExprSyntax(stringLiteral)
}
