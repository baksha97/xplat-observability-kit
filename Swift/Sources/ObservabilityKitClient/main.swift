import Foundation
import Tracing

/// Tracer that prints all operations for debugging purposes
public struct PrintTracer: Tracer {
  public typealias Span = PrintSpan
  
  public init() {}
  
  public func startAnySpan<Instant: TracerInstant>(
    _ operationName: String,
    context: @autoclosure () -> ServiceContext,
    ofKind kind: SpanKind,
    at instant: @autoclosure () -> Instant,
    function: String,
    file fileID: String,
    line: UInt
  ) -> any Tracing.Span {
    print("Starting span: \(operationName) of kind \(kind) at \(instant())")
    print("Location: \(function) in \(fileID):\(line)")
    return PrintSpan(
      operationName: operationName,
      context: context(),
      startTime: instant(),
      kind: kind,
      location: (function, fileID, line)
    )
  }
  
  public func forceFlush() {
    print("Force flush requested")
  }
  
  public func inject<Carrier, Inject>(
    _ context: ServiceContext,
    into carrier: inout Carrier,
    using injector: Inject
  ) where Inject: Injector, Carrier == Inject.Carrier {
    print("Injecting context into carrier")
  }
  
  public func extract<Carrier, Extract>(
    _ carrier: Carrier,
    into context: inout ServiceContext,
    using extractor: Extract
  ) where Extract: Extractor, Carrier == Extract.Carrier {
    print("Extracting context from carrier")
  }
}

extension PrintTracer {
  public func startSpan<Instant: TracerInstant>(
    _ operationName: String,
    context: @autoclosure () -> ServiceContext,
    ofKind kind: SpanKind,
    at instant: @autoclosure () -> Instant,
    function: String,
    file fileID: String,
    line: UInt
  ) -> PrintSpan {
    PrintSpan(
      operationName: operationName,
      context: context(),
      startTime: instant(),
      kind: kind,
      location: (function, fileID, line)
    )
  }
}

public final class PrintSpan: Tracing.Span, @unchecked Sendable {
  public let context: ServiceContext
  private let startTime: any TracerInstant
  
  // Thread-safe storage using a serial queue
  private let queue = DispatchQueue(label: "com.print-span.storage")
  public var operationName: String
  public var attributes: SpanAttributes
  private var kind: SpanKind
  private var location: (function: String, file: String, line: UInt)
  private var status: SpanStatus?
  private var links: [SpanLink] = []
  private var events: [SpanEvent] = []
  private var errors: [(error: Error, attributes: SpanAttributes, timestamp: any TracerInstant)] = []
  private var endTime: (any TracerInstant)?
  
  public var isRecording: Bool {
    queue.sync { endTime == nil }
  }
  
  public init(
    operationName: String,
    context: ServiceContext,
    startTime: any TracerInstant,
    kind: SpanKind,
    location: (String, String, UInt)
  ) {
    self.context = context
    self.startTime = startTime
    self.operationName = operationName
    self.attributes = [:]
    self.kind = kind
    self.location = location
  }
  
  public func setStatus(_ status: SpanStatus) {
    queue.sync { self.status = status }
  }
  
  public func addLink(_ link: SpanLink) {
    queue.sync { links.append(link) }
  }
  
  public func addEvent(_ event: SpanEvent) {
    queue.sync { events.append(event) }
  }
  
  public func recordError<Instant: TracerInstant>(
    _ error: Error,
    attributes: SpanAttributes,
    at instant: @autoclosure () -> Instant
  ) {
    queue.sync { errors.append((error, attributes, instant())) }
  }
  
  public func end<Instant: TracerInstant>(at instant: @autoclosure () -> Instant) {
    queue.sync {
      // Only end once
      guard endTime == nil else { return }
      
      endTime = instant()
      print("""
            === Span Completed ===
            Operation: \(operationName)
            Kind: \(kind)
            Location: \(location.function) in \(location.file):\(location.line)
            Duration: \(startTime) to \(endTime ?? startTime)
            
            Attributes: \(attributes)
            Context: \(context)
            
            Events (\(events.count)):
            \(events.map { "- \($0.name): \($0.attributes)" }.joined(separator: "\n"))
            
            Links (\(links.count)):
            \(links.map { "- \($0)" }.joined(separator: "\n"))
            
            Errors (\(errors.count)):
            \(errors.map { "- \($0.error) at \($0.timestamp) with attributes: \($0.attributes)" }.joined(separator: "\n"))
            ===================
            
            """)
    }
  }
}

InstrumentationSystem.bootstrap(PrintTracer())

struct Failed: Error {}
do {
  try await withSpan("testing-spans") { span in
    try await Task.sleep(nanoseconds: 10000)
    throw Failed()
  }
}
catch {
  print("failed with: \(error)")
}
