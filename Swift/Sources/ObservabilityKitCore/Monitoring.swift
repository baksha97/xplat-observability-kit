import Foundation
import Dependencies
import DependenciesMacros


@DependencyClient
public struct CollectorClient : Sendable {
  public var collect: @Sendable (MonitorData) -> Void
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

public struct MonitorData: Sendable {
  public typealias Duration = CFAbsoluteTime
  public let key: String
  public let duration: Duration
  public let error: Error?
}

public protocol Capturing {
  func withCapture<T>(_ key: String, _ operation: () throws -> T) rethrows -> T
  func withCapture<T>(_ key: String, _ operation: () async throws -> T) async rethrows -> T
}

public extension Capturing {
  
  func withCapture<T>(_ key: String, _ operation: () async throws -> T) async rethrows -> T {
    let start = CFAbsoluteTimeGetCurrent()
    var failure: Error?
    defer {
      dispatch(
        .init(
          key: key,
          duration: start - CFAbsoluteTimeGetCurrent(),
          error: failure
        )
      )
    }
    do {
      let result = try await operation()
      return result
    } catch {
      failure = error
      throw error
    }
  }
  
  func withCapture<T>(_ key: String, _ operation: () throws -> T) rethrows -> T {
    let start = CFAbsoluteTimeGetCurrent()
    var failure: Error?
    defer {
      dispatch(
        .init(
          key: key,
          duration: start - CFAbsoluteTimeGetCurrent(),
          error: failure
        )
      )
    }
    do {
      let result = try operation()
      return result
    } catch {
      failure = error
      throw error
    }
  }

  fileprivate func dispatch(_ capture: MonitorData) {
    @Dependency(CollectorClient.self) var client
    client.collect(capture)
  }
}
