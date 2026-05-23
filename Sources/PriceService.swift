import Foundation

class PriceService {
    private let session = URLSession.shared
    private let baseURL = "https://data-api.binance.vision/api/v3/ticker/price?symbol="

    static let supportedCoins: [(symbol: String, name: String, icon: String)] = [
        ("BTC", "Bitcoin", "₿"),
        ("ETH", "Ethereum", "Ξ"),
        ("SOL", "Solana", "◎"),
        ("BNB", "BNB", "◆"),
        ("XRP", "XRP", "✕"),
        ("DOGE", "Dogecoin", "Ð"),
        ("ADA", "Cardano", "₳"),
        ("AVAX", "Avalanche", "▲"),
        ("DOT", "Polkadot", "●"),
        ("MATIC", "Polygon", "⬡"),
        ("LINK", "Chainlink", "⬡"),
        ("UNI", "Uniswap", "🦄"),
    ]

    struct PriceResponse: Codable {
        let symbol: String
        let price: String
    }

    func fetchPrice(symbol: String) async throws -> Double {
        let url = URL(string: baseURL + symbol + "USDT")!
        let (data, _) = try await session.data(from: url)
        let response = try JSONDecoder().decode(PriceResponse.self, from: data)
        guard let price = Double(response.price) else {
            throw URLError(.cannotParseResponse)
        }
        return price
    }

    static func formatPrice(_ price: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = "USD"
        formatter.currencySymbol = "$"
        formatter.maximumFractionDigits = 2
        formatter.minimumFractionDigits = 2
        return formatter.string(from: NSNumber(value: price)) ?? "$\(price)"
    }

    static func formatShortPrice(_ price: Double) -> String {
        if price >= 1000 {
            let short = price / 1000
            let formatter = NumberFormatter()
            formatter.maximumFractionDigits = 1
            formatter.minimumFractionDigits = 1
            let formatted = formatter.string(from: NSNumber(value: short)) ?? "\(short)"
            return "$\(formatted)K"
        }
        return formatPrice(price)
    }

    static func icon(for symbol: String) -> String {
        supportedCoins.first(where: { $0.symbol == symbol })?.icon ?? "●"
    }
}
