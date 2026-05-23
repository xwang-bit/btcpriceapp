// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "BTCPriceApp",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "BTCPriceApp",
            path: "Sources"
        )
    ]
)
