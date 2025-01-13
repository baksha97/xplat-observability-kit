# 🚀 Capture & Monitorable

**A pair of complementary instrumentation libraries for Swift and Kotlin that provide lightweight, zero-overhead method monitoring through compile-time code generation.** 

---

## 🤔 Why Two Libraries?

Although these libraries may feel different in practice, there is hope for a future where they can be consolidated into a common API with shared resources. 

---

## 🌟 Common Design Principles

- **Zero Runtime Reflection**: All monitoring code is generated at compile-time via macros (Swift) or KSP (Kotlin)  
- **Minimal Runtime Overhead**: Optimized for performance through careful API design and compile-time code generation  
- **Flexible Collection**: Pluggable collectors that can be composed and customized  
- **Simple API Surface**: Single-annotation approach to reduce cognitive overhead  
- **Built-in Error Handling**: Automatic capture of errors/exceptions with proper type safety  

---

## 🛠️ Platform-Specific Approaches

### 🍎 Capture (Swift)
```swift
@Capture
func fetchData() async throws -> Data {
    return try await networkClient.requestData()
}
```

### 🤖 Monitorable (Kotlin)
```kotlin
@Monitor.Function(name = "fetch_data")
suspend fun fetchData(): Result<Data> {
    return networkClient.requestData()
}
```

> **Note:** While the syntax differs to match platform conventions, both libraries achieve the same goal: automatic instrumentation with minimal developer effort.

---

## 🔍 Key Differences

- **Swift** uses the new macro system for compile-time code generation, while **Kotlin** leverages KSP (Kotlin Symbol Processing).  
- **Swift** focuses on function-level annotations, while **Kotlin** uses interface-based monitoring.  
- Error handling approaches differ to match platform idioms (Swift's `throws` vs Kotlin's `Result`).  

---

## ✨ Shared Features

Both libraries provide:

- ⏱️ Automatic duration measurement  
- ❌ Error/exception tracking  
- ♻️ Customizable metric collection  
- ⚙️ Support for async/concurrent code  
- 🏎️ Zero-overhead monitoring  
- 🔒 Type-safe generated code  

---

## 💡 Why Use These Libraries?

1. **Simplified Instrumentation**: Add a single annotation to monitor any function  
2. **Production Ready**: Zero reflection and minimal overhead make both libraries suitable for production use  
3. **Ecosystem Integration**: Each library follows platform-specific best practices and integrates naturally with existing codebases  
4. **Flexible Collection**: Push metrics to any monitoring system through custom collectors  
5. **Cross-Platform Consistency**: Use similar monitoring patterns across Swift and Kotlin codebases  

---

## 📚 Getting Started

Check out the individual library READMEs for platform-specific installation and usage instructions.

---

## 📜 License

Both libraries are available under **[LICENSE NAME]**.  
