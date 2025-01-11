// Sources/ObservabilityKitClient/main.swift

import ObservabilityKitCore

@Monitored
protocol UserService {
  func getUser(id: String) throws -> User
  func createUser(_ user: User) throws -> User
}
//
//struct UserServiceProxy: UserService, Capturing {
//  let underlying: UserService
//  let collector: any Monitor.Collector
//  init(underlying: UserService, collector: any Monitor.Collector) {
//    self.underlying = underlying
//    self.collector = collector
//  }
//  
//  func getUser(id: String) throws -> User {
//    try withThrowingCapture(key: "getUser") {
//      try underlying.getUser(id: id)
//    }
//  }
//  
//  func createUser(_ user: User) throws -> User {
//    try withThrowingCapture(key: "createUser") {
//      try underlying.createUser(user)
//    }
//  }
//}
//
//extension UserService {
//  func monitored(collector: any Monitor.Collector) -> UserService {
//    UserServiceProxy(underlying: self, collector: collector)
//  }
//}

struct User {
  let id: String
  let name: String
}

// Example implementation
class UserServiceImpl: UserService {
  func monitored(collectors: any ObservabilityKitCore.Monitor.Collector...) -> Self {
    fatalError()
  }
  
  func getUser(id: String) throws -> User {
    return User(id: id, name: "John Doe")
  }
  
  func createUser(_ user: User) throws -> User {
    return user
  }
}

// Usage
let service = UserServiceImpl()
let monitoredService = service.monitored()

do {
  let user = try monitoredService.getUser(id: "123")
  print("Got user: \(user)")
  
  let newUser = try monitoredService.createUser(User(id: "456", name: "Jane Doe"))
  print("Created user: \(newUser)")
} catch {
  print("Error: \(error)")
}
