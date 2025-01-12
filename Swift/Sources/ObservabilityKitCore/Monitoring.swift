import Foundation

public enum Monitor {
  /// Data structure representing a monitoring event
  public struct Data {
    public let key: String
    public let durationMillis: Int64
    public let error: Error?
    
    public init(key: String, durationMillis: Int64, error: Error? = nil) {
      self.key = key
      self.durationMillis = durationMillis
      self.error = error
    }
  }
  
  /// Protocol for collecting monitoring data
  public protocol Collector {
    func collect(_ data: Data)
  }
  
  /// Built-in collectors
  public enum Collectors {
    public class Printer: Collector {
      public init() {}
      
      public func collect(_ data: Data) {
        print(data)
      }
    }
    
    public class Composite: Collector {
      private let collectors: [Collector]
      
      public init(_ collectors: Collector...) {
        self.collectors = collectors
      }
      
      public func collect(_ data: Data) {
        collectors.forEach { $0.collect(data) }
      }
    }
  }
}

// Protocol for capturing method execution metrics
public protocol Capturing {
  var collector: Monitor.Collector { get }
  
  func withThrowingCapture<T>(key: String, operation: () throws -> T) rethrows -> T
  func withResultCapture<T>(key: String, operation: () -> Result<T, Error>) -> Result<T, Error>
}

// Default implementation
public extension Capturing {
  func withThrowingCapture<T>(key: String, operation: () throws -> T) rethrows -> T {
    let start = DispatchTime.now()
    do {
      let result = try operation()
      let end = DispatchTime.now()
      let duration = end.uptimeNanoseconds - start.uptimeNanoseconds
      collector.collect(Monitor.Data(key: key, durationMillis: Int64(duration / 1_000_000)))
      return result
    } catch {
      let end = DispatchTime.now()
      let duration = end.uptimeNanoseconds - start.uptimeNanoseconds
      collector.collect(Monitor.Data(key: key, durationMillis: Int64(duration / 1_000_000), error: error))
      throw error
    }
  }
  
  func withResultCapture<T, E>(key: String, operation: () -> Result<T, E>) -> Result<T, E> {
    let start = DispatchTime.now()
    let result = operation()
    let end = DispatchTime.now()
    let duration = end.uptimeNanoseconds - start.uptimeNanoseconds
    
    switch result {
    case .success:
      collector.collect(Monitor.Data(key: key, durationMillis: Int64(duration / 1_000_000)))
    case .failure(let error):
      collector.collect(Monitor.Data(key: key, durationMillis: Int64(duration / 1_000_000), error: error))
    }
    
    return result
  }
}

// Example Macro Input
public protocol Service {
  func sample() throws -> String
}


// Example Macro Ouput

public struct ServiceProxy: Service, Capturing {
  public let collector: any Monitor.Collector
  public let underlying: Service
  public let clock: any Clock
  
  public func sample() throws -> String {
    try underlying.sample()
  }
}
