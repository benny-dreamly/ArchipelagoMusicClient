// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "ProtocolCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(name: "ProtocolCore", targets: ["ProtocolCore"])
    ],
    targets: [
        .target(name: "ProtocolCore"),
        .testTarget(name: "ProtocolCoreTests", dependencies: ["ProtocolCore"])
    ]
)