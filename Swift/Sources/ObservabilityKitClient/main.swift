import ObservabilityKitCore
import Dependencies


struct SampleError: Error {}
struct Sample: Capturing {
  
  @Captured
  @CaptureKey("test")
  func nonThrowingSync() {
  }
  
  @Captured
  func syncThrowWithoutThrow() throws {
    print()
  }
  
  
  @Captured
  func syncThrowWithThrow() throws {
    throw SampleError()
  }
  
  @Captured
  func asyncThrowWithoutThrow() async throws {
    print()
  }
}

let sample = Sample()
sample.nonThrowingSync()
print("======")
//try sample.syncThrowWithoutThrow()
//print("======")
//try await sample.asyncThrowWithoutThrow()
//print("======")

//try sample.syncThrowWithThrow()
//try await sample.asyncThrowWithThrow()

