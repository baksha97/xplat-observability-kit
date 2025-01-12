import Foundation
import Dependencies
import DependenciesMacros


@DependencyClient
struct CollectorClient {
  var collect: @Sendable (CaptureData) -> Void
}

extension CollectorClient: DependencyKey {
  static let liveValue = Self(
    collect: { data in
      print("Live Collector:")
      print("Method: \(data.key)")
      print("Duration: \(String(format: "%.2f", data.durationMillis))ms")
      if let error = data.exception {
        print("Exception: \(error)")
      }
    }
  )
  
  static let testValue = Self(
    collect: { _ in }  // No-op collector for testing
  )
}

extension DependencyValues {
  var collector: CollectorClient {
    get { self[CollectorClient.self] }
    set { self[CollectorClient.self] = newValue }
  }
}

struct CaptureData: Sendable {
  let key: String
  let durationMillis: Double
  let error: Error?
}
struct Measurment<T> {
  let result: Result<T, Error>
  let duration: CFAbsoluteTime
}

extension Measurment {
  var error: Error? {
    if case let Result.failure(error) = result {
      error
    } else {
      nil
    }
  }
}
protocol Capturing {
  func capture<T>(_ key: String, _ operation: () throws -> T) rethrows -> T
  func capture<T>(_ key: String, _ operation: () async throws -> T) async rethrows -> T
}

extension Capturing {
  // Call can throw, but the error is not handled; a function declared 'rethrows' may only throw if its parameter does
  func capture<T>(_ key: String, _ operation: () async throws -> T) async rethrows -> T {
    let measured = await measure(operation)
    dispatch(
      CaptureData(
        key: key,
        durationMillis: measured.duration,
        error: measured.error
      )
    )
    return try measured.result.get()
  }
  
  private func measure<T>(
    _ operation: () async throws -> T
  ) async -> Measurment<T> {
    let start = CFAbsoluteTimeGetCurrent()
    let result = await Result { try await operation() }
    let end = CFAbsoluteTimeGetCurrent()
    return Measurment(result: result, duration: start - end)
  }
  
  private func measure<T>(
    _ operation: () throws -> T
  ) -> (Result<T, Error>, CFAbsoluteTime) {
    let start = CFAbsoluteTimeGetCurrent()
    let result = Result { try operation() }
    let end = CFAbsoluteTimeGetCurrent()
    return (result, start - end)
  }
  
  private func dispatch(_ capture: CaptureData) {
    @Dependency(CollectorClient.self) var client
    client.collect(capture)
  }
}

//struct Sample: Capturing {
//  
//  func sample() throws {
//    try capture("sample") {
//      print("in capture")
//      // Uncomment to test error handling:
//      // throw NSError(domain: "TestError", code: -1, userInfo: nil)
//    }
//  }
//}
//
//// Example of testing setup:
//let testingCollector = CollectorClient(
//  collect: { data in
//    // Verify the captured data in tests
//    print("Test collector received:", data)
//  }
//)
//
//let testingSample = Sample()
////try testingSample.sample()
//
//func test() {
//  @Dependency(\.continuousClock) var clock
//  var duration = clock.measure {
//    print("measuring")
//  }
//  print(duration)
//}
//
//test()
