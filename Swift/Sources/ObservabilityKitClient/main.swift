import ObservabilityKitCore
import Dependencies

struct SampleError: Error {}
struct Sample: Capturing {
  
  func nonThrowingSync() {
    withCapture("nonThrowingSync") {
      print("nonThrowingSync: in capture")
    }
  }
  
  func syncThrowWithoutThrow() throws {
    try withThrowingCapture("syncThrowWithoutThrow") {
      print("syncThrowWithoutThrow: in capture")
      print("in capture")
    }
  }
  
  func syncThrowWithThrow() throws {
    try withThrowingCapture("syncThrowWithThrow") {
      print("syncThrowWithThrow: in capture")
      throw SampleError()
    }
  }
  
  func asyncThrowWithoutThrow() async throws {
    try await withThrowingCapture("asyncThrowWithoutThrow") {
      print("asyncThrowWithoutThrow: in capture")
    }
  }
  
  func asyncThrowWithThrow() async throws {
    try await withThrowingCapture("asyncThrowWithThrow") {
      throw SampleError()
    }
  }
}

let sample = Sample()

sample.nonThrowingSync()
try sample.syncThrowWithoutThrow()
try await sample.asyncThrowWithoutThrow()

//try sample.syncThrowWithThrow()
//try await sample.asyncThrowWithThrow()

