import Testing
import Dependencies
@testable import ObservabilityKitCore

class TestCapture: Capturing {}

actor TestCollectorStore {
    private(set) var capturedData: [CaptureData] = []
    
    func capture(_ data: CaptureData) {
        capturedData.append(data)
    }
    
    func getData() -> [CaptureData] {
        capturedData
    }
    
    func reset() {
        capturedData = []
    }
}

@Suite("Capturing Tests")
struct CapturingTests {
    @Test("Non-throwing sync operation captures data correctly")
    func testNonThrowingSync() async throws {
        let store = TestCollectorStore()
        let testCollector = CollectorClient(
            collect: { data in
                Task { await store.capture(data) }
            }
        )
        
        let sut = TestCapture()
        withDependencies { $0.collector = testCollector } operation: {
            _ = sut.withCapture("test.nonThrowing") {
                return "success"
            }
        }
        
        
        let capturedData = await store.getData()
        #expect(capturedData.count == 1)
        #expect(capturedData[0].key == "test.nonThrowing")
        #expect(capturedData[0].error == nil)
    }
    
    @Test("Throwing sync operation captures error")
    func testThrowingSync() async throws {
        struct TestError: Error {}
        let store = TestCollectorStore()
        let testCollector = CollectorClient(
            collect: { data in
                Task { await store.capture(data) }
            }
        )
        
        let sut = TestCapture()
        withDependencies { $0.collector = testCollector } operation: {
            do {
                _ = try sut.withThrowingCapture("test.throwing") {
                    throw TestError()
                }
            } catch {
              #expect(error is TestError)
            }
        }
        
        let capturedData = await store.getData()
        #expect(capturedData.count == 1)
        #expect(capturedData[0].key == "test.throwing")
        #expect(capturedData[0].error != nil)
    }
    
    @Test("Non-throwing async operation captures data correctly")
    func testNonThrowingAsync() async throws {
        let store = TestCollectorStore()
        let testCollector = CollectorClient(
            collect: { data in
                Task { await store.capture(data) }
            }
        )
        
        let sut = TestCapture()
        withDependencies { $0.collector = testCollector } operation: {
            _ = sut.withCapture("test.asyncNonThrowing") { "async success" }
        }
      
        let capturedData = await store.getData()
        #expect(capturedData.count == 1)
        #expect(capturedData[0].key == "test.asyncNonThrowing")
        #expect(capturedData[0].error == nil)
    }
    
    @Test("Throwing async operation captures error")
    func testThrowingAsync() async throws {
        struct TestError: Error {}
        let store = TestCollectorStore()
        let testCollector = CollectorClient(
            collect: { data in
                Task { await store.capture(data) }
            }
        )
        
        let sut = TestCapture()
        withDependencies { $0.collector = testCollector } operation: {
            do {
                _ = try sut.withThrowingCapture("test.asyncThrowing") {
                    throw TestError()
                }
            } catch {
              #expect(error is TestError)
            }
        }
      
        let capturedData = await store.getData()
        #expect(capturedData.count == 1)
        #expect(capturedData[0].key == "test.asyncThrowing")
        #expect(capturedData[0].error != nil)
    }
}
