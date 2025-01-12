import Foundation
import Dependencies
import DependenciesMacros


@DependencyClient
public struct CollectorClient : Sendable {
  public var collect: @Sendable (CaptureData) -> Void
}

extension CollectorClient: DependencyKey {
  public static let liveValue = Self(
    collect: { data in
      print("Live Collector:")
      print("Method: \(data.key)")
      print("Duration: \(String(format: "%.2f", data.duration))ms")
      if let error = data.error {
        print("Error: \(error)")
      }
    }
  )
  
  public static let testValue = Self(
    collect: { _ in }  // No-op collector for testing
  )
}

public extension DependencyValues {
  var collector: CollectorClient {
    get { self[CollectorClient.self] }
    set { self[CollectorClient.self] = newValue }
  }
}

public struct CaptureData: Sendable {
  public typealias Duration = CFAbsoluteTime
  public let key: String
  public let duration: Duration
  public let error: Error?
}
public struct Measurment<T> {
  public let result: Result<T, Error>
  public let duration: CaptureData.Duration
}

public extension Measurment {
  var error: Error? {
    if case let Result.failure(error) = result {
      error
    } else {
      nil
    }
  }
}
public protocol Capturing {
  func withCapture<T>(_ key: String, _ operation: () -> T) -> T
  func withCapture<T>(_ key: String, _ operation: () async -> T) async -> T
  func withThrowingCapture<T>(_ key: String, _ operation: () throws -> T) throws -> T
  func withThrowingCapture<T>(_ key: String, _ operation: () async throws -> T) async throws -> T
}

public extension Capturing {
  
  func withCapture<T>(_ key: String, _ operation: () async -> T) async -> T {
    // The underlying operation can never fail, so we can ignore the compiler warning.
    try! await withThrowingCapture(key, operation)
  }
  
  func withCapture<T>(_ key: String, _ operation: () -> T) -> T {
    // The underlying operation can never fail, so we can ignore the compiler warning.
    try! withThrowingCapture(key, operation)
  }
  
  func withThrowingCapture<T>(_ key: String, _ operation: () async throws -> T) async throws -> T {
    let measured = await measure(operation)
    dispatch(
      CaptureData(
        key: key,
        duration: measured.duration,
        error: measured.error
      )
    )
    return try measured.result.get()
  }
  
  func withThrowingCapture<T>(_ key: String, _ operation: () throws -> T) throws -> T {
    let measured = measure(operation)
    dispatch(
      CaptureData(
        key: key,
        duration: measured.duration,
        error: measured.error
      )
    )
    return try measured.result.get()
  }
  
  fileprivate func measure<T>(
    _ operation: () async throws -> T
  ) async -> Measurment<T> {
    let start = CFAbsoluteTimeGetCurrent()
    let result = await Result { try await operation() }
    let end = CFAbsoluteTimeGetCurrent()
    return Measurment(result: result, duration: start - end)
  }
  
  fileprivate func measure<T>(
    _ operation: () throws -> T
  ) -> Measurment<T> {
    let start = CFAbsoluteTimeGetCurrent()
    let result = Result { try operation() }
    let end = CFAbsoluteTimeGetCurrent()
    return Measurment(result: result, duration: start - end)
  }
  
  fileprivate func dispatch(_ capture: CaptureData) {
    @Dependency(CollectorClient.self) var client
    client.collect(capture)
  }
}
