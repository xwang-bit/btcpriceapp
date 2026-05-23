import AppKit

// MARK: - Price Chart View

class PriceChartView: NSView {
    var priceHistory: [(time: Date, price: Double)] = []

    override var isFlipped: Bool { false }

    override func draw(_ dirtyRect: NSRect) {
        super.draw(dirtyRect)

        guard let ctx = NSGraphicsContext.current?.cgContext, priceHistory.count >= 2 else {
            drawEmptyState()
            return
        }

        let prices = priceHistory.map { $0.price }
        let minPrice = prices.min()!
        let maxPrice = prices.max()!
        let priceRange = maxPrice - minPrice

        let padding: CGFloat = 8
        let chartRect = bounds.insetBy(dx: padding, dy: padding)

        // Background
        ctx.setFillColor(NSColor.windowBackgroundColor.cgColor)
        let bgPath = CGPath(roundedRect: chartRect, cornerWidth: 6, cornerHeight: 6, transform: nil)
        ctx.addPath(bgPath)
        ctx.fillPath()

        let drawRect = chartRect.insetBy(dx: 8, dy: 16)
        let stepX = drawRect.width / CGFloat(priceHistory.count - 1)
        let scaleY: (Double) -> CGFloat = { price in
            if priceRange == 0 { return drawRect.midY }
            return drawRect.minY + CGFloat((price - minPrice) / priceRange) * drawRect.height
        }

        // Build line path
        let linePath = CGMutablePath()
        for (i, price) in prices.enumerated() {
            let x = drawRect.minX + CGFloat(i) * stepX
            let y = scaleY(price)
            if i == 0 {
                linePath.move(to: CGPoint(x: x, y: y))
            } else {
                linePath.addLine(to: CGPoint(x: x, y: y))
            }
        }

        // Gradient fill
        let isUp = prices.last! >= prices.first!
        let lineColor = isUp ? NSColor.systemGreen : NSColor.systemRed
        let fillColor = isUp
            ? NSColor.systemGreen.withAlphaComponent(0.15)
            : NSColor.systemRed.withAlphaComponent(0.15)

        let fillPath = linePath.mutableCopy()!
        let lastX = drawRect.minX + CGFloat(prices.count - 1) * stepX
        let firstX = drawRect.minX
        fillPath.addLine(to: CGPoint(x: lastX, y: drawRect.minY))
        fillPath.addLine(to: CGPoint(x: firstX, y: drawRect.minY))
        fillPath.closeSubpath()

        ctx.saveGState()
        ctx.addPath(fillPath)
        ctx.clip()
        let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: [fillColor.cgColor, NSColor.clear.cgColor] as CFArray,
            locations: [0, 1]
        )!
        ctx.drawLinearGradient(
            gradient,
            start: CGPoint(x: drawRect.midX, y: drawRect.maxY),
            end: CGPoint(x: drawRect.midX, y: drawRect.minY),
            options: []
        )
        ctx.restoreGState()

        // Line
        ctx.setStrokeColor(lineColor.cgColor)
        ctx.setLineWidth(1.5)
        ctx.addPath(linePath)
        ctx.strokePath()

        // Current price dot
        let lastY = scaleY(prices.last!)
        ctx.setFillColor(lineColor.cgColor)
        ctx.addArc(center: CGPoint(x: lastX, y: lastY), radius: 3, startAngle: 0, endAngle: .pi * 2, clockwise: false)
        ctx.fillPath()

        // Price labels
        let attrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.monospacedDigitSystemFont(ofSize: 9, weight: .medium),
            .foregroundColor: NSColor.secondaryLabelColor
        ]
        let maxStr = PriceService.formatShortPrice(maxPrice)
        let minStr = PriceService.formatShortPrice(minPrice)
        (maxStr as NSString).draw(at: CGPoint(x: drawRect.minX + 2, y: drawRect.maxY - 12), withAttributes: attrs)
        (minStr as NSString).draw(at: CGPoint(x: drawRect.minX + 2, y: drawRect.minY + 2), withAttributes: attrs)
    }

    private func drawEmptyState() {
        guard let ctx = NSGraphicsContext.current?.cgContext else { return }
        let padding: CGFloat = 8
        let chartRect = bounds.insetBy(dx: padding, dy: padding)
        ctx.setFillColor(NSColor.windowBackgroundColor.cgColor)
        let bgPath = CGPath(roundedRect: chartRect, cornerWidth: 6, cornerHeight: 6, transform: nil)
        ctx.addPath(bgPath)
        ctx.fillPath()

        let text = "Collecting data..."
        let attrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 11),
            .foregroundColor: NSColor.tertiaryLabelColor
        ]
        let size = (text as NSString).size(withAttributes: attrs)
        let point = CGPoint(x: bounds.midX - size.width / 2, y: bounds.midY - size.height / 2)
        (text as NSString).draw(at: point, withAttributes: attrs)
    }
}

// MARK: - Popover Content View

class PricePopoverView: NSView {
    let chartView = PriceChartView()
    private let priceLabel = NSTextField(labelWithString: "")
    private let timeLabel = NSTextField(labelWithString: "")
    private let symbolLabel = NSTextField(labelWithString: "")
    private let pnlLabel = NSTextField(labelWithString: "")
    private let settingsButton = NSButton()
    private let quitButton = NSButton()

    var onSettings: (() -> Void)?
    var onQuit: (() -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        setupViews()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupViews()
    }

    private func setupViews() {
        symbolLabel.font = NSFont.systemFont(ofSize: 12, weight: .medium)
        symbolLabel.textColor = .secondaryLabelColor
        addSubview(symbolLabel)

        priceLabel.font = NSFont.monospacedDigitSystemFont(ofSize: 22, weight: .bold)
        addSubview(priceLabel)

        timeLabel.font = NSFont.systemFont(ofSize: 10)
        timeLabel.textColor = .tertiaryLabelColor
        addSubview(timeLabel)

        pnlLabel.font = NSFont.monospacedDigitSystemFont(ofSize: 13, weight: .semibold)
        addSubview(pnlLabel)

        chartView.wantsLayer = true
        addSubview(chartView)

        settingsButton.title = "Settings"
        settingsButton.bezelStyle = .accessoryBarAction
        settingsButton.font = NSFont.systemFont(ofSize: 11)
        settingsButton.target = self
        settingsButton.action = #selector(settingsClicked)
        addSubview(settingsButton)

        quitButton.title = "Quit"
        quitButton.bezelStyle = .accessoryBarAction
        quitButton.font = NSFont.systemFont(ofSize: 11)
        quitButton.target = self
        quitButton.action = #selector(quitClicked)
        addSubview(quitButton)
    }

    override func layout() {
        super.layout()
        let w = bounds.width
        symbolLabel.frame = NSRect(x: 16, y: bounds.height - 28, width: w - 32, height: 16)
        priceLabel.frame = NSRect(x: 16, y: bounds.height - 62, width: w / 2, height: 30)
        pnlLabel.frame = NSRect(x: w / 2 + 8, y: bounds.height - 72, width: w / 2 - 24, height: 44)
        timeLabel.frame = NSRect(x: 16, y: bounds.height - 80, width: w - 32, height: 14)
        chartView.frame = NSRect(x: 8, y: 36, width: w - 16, height: 120)
        quitButton.frame = NSRect(x: 16, y: 8, width: 50, height: 22)
        settingsButton.frame = NSRect(x: w - 80, y: 8, width: 64, height: 22)
    }

    func update(symbol: String, price: Double?, history: [(time: Date, price: Double)]) {
        let icon = PriceService.icon(for: symbol)
        symbolLabel.stringValue = "\(icon) \(symbol)/USDT"

        if let price = price {
            priceLabel.stringValue = PriceService.formatPrice(price)

            let purchasePrices = UserDefaults.standard.dictionary(forKey: "purchasePrices") as? [String: Double] ?? [:]
            if let buyPrice = purchasePrices[symbol], buyPrice > 0 {
                let pnl = (price - buyPrice) / buyPrice * 100
                let isProfit = pnl >= 0
                let sign = isProfit ? "+" : ""
                let pnlText = "\(sign)\(String(format: "%.2f", pnl))%"

                let attr = NSMutableAttributedString()
                // Arrow
                attr.append(NSAttributedString(
                    string: isProfit ? "▲ " : "▼ ",
                    attributes: [
                        .font: NSFont.systemFont(ofSize: 11, weight: .bold),
                        .foregroundColor: isProfit ? NSColor.systemGreen : NSColor.systemRed
                    ]
                ))
                // P&L percentage
                attr.append(NSAttributedString(
                    string: pnlText,
                    attributes: [
                        .font: NSFont.monospacedDigitSystemFont(ofSize: 14, weight: .bold),
                        .foregroundColor: isProfit ? NSColor.systemGreen : NSColor.systemRed
                    ]
                ))
                // Cost basis
                attr.append(NSAttributedString(
                    string: "\nCost: \(PriceService.formatPrice(buyPrice))",
                    attributes: [
                        .font: NSFont.monospacedDigitSystemFont(ofSize: 10, weight: .regular),
                        .foregroundColor: NSColor.tertiaryLabelColor
                    ]
                ))
                pnlLabel.attributedStringValue = attr
            } else {
                pnlLabel.stringValue = ""
            }
        } else {
            priceLabel.stringValue = "Loading..."
            pnlLabel.stringValue = ""
        }

        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        timeLabel.stringValue = "Updated: \(formatter.string(from: Date()))"

        chartView.priceHistory = history
        chartView.needsDisplay = true
    }

    @objc private func settingsClicked() {
        onSettings?()
    }

    @objc private func quitClicked() {
        onQuit?()
    }
}

// MARK: - App Delegate

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let priceService = PriceService()
    private var timer: Timer?
    private var currentPrice: Double?
    private var settingsWindowController: SettingsWindowController?
    private var priceHistory: [(time: Date, price: Double)] = []
    private var popover: NSPopover!
    private var popoverView: PricePopoverView!
    private var eventMonitor: Any?

    private var selectedSymbol: String {
        get { UserDefaults.standard.string(forKey: "selectedSymbol") ?? "BTC" }
        set {
            UserDefaults.standard.set(newValue, forKey: "selectedSymbol")
            priceHistory.removeAll()
            fetchAndUpdate()
        }
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        setupPopover()
        setupStatusBar()
        fetchAndUpdate()
        timer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
            self?.fetchAndUpdate()
        }
        eventMonitor = NSEvent.addGlobalMonitorForEvents(matching: [.leftMouseDown, .rightMouseDown]) { [weak self] _ in
            self?.closePopover()
        }
    }

    private func setupPopover() {
        popoverView = PricePopoverView(frame: NSRect(x: 0, y: 0, width: 300, height: 240))
        popoverView.onSettings = { [weak self] in
            self?.closePopover()
            self?.openSettings()
        }
        popoverView.onQuit = {
            NSApplication.shared.terminate(nil)
        }

        popover = NSPopover()
        popover.contentSize = NSSize(width: 300, height: 240)
        popover.behavior = .transient
        popover.animates = true
        popover.contentViewController = NSViewController()
        popover.contentViewController?.view = popoverView
    }

    private func setupStatusBar() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)

        guard let button = statusItem.button else { return }
        button.title = "... ..."
        button.action = #selector(statusBarButtonClicked(_:))
        button.target = self
        button.sendAction(on: [.leftMouseUp, .rightMouseUp])
    }

    @objc private func statusBarButtonClicked(_ sender: NSStatusBarButton) {
        let event = NSApp.currentEvent!
        if event.type == .rightMouseUp {
            showMenu()
        } else {
            togglePopover()
        }
    }

    private func togglePopover() {
        if popover.isShown {
            closePopover()
        } else {
            guard let button = statusItem.button else { return }
            popoverView.update(symbol: selectedSymbol, price: currentPrice, history: priceHistory)
            popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)
        }
    }

    private func closePopover() {
        popover.performClose(nil)
    }

    private func showMenu() {
        let menu = NSMenu()

        if let price = currentPrice {
            let priceItem = NSMenuItem(title: PriceService.formatPrice(price), action: nil, keyEquivalent: "")
            priceItem.attributedTitle = NSAttributedString(
                string: PriceService.formatPrice(price),
                attributes: [.font: NSFont.boldSystemFont(ofSize: 18)]
            )
            menu.addItem(priceItem)
        }

        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        menu.addItem(NSMenuItem(title: "Updated: \(formatter.string(from: Date()))", action: nil, keyEquivalent: ""))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Settings...", action: #selector(openSettings), keyEquivalent: ","))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Refresh", action: #selector(refresh), keyEquivalent: "r"))
        menu.addItem(NSMenuItem.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(quit), keyEquivalent: "q"))
        menu.items.forEach { $0.target = self }

        statusItem.menu = menu
        statusItem.button?.performClick(nil)
        statusItem.menu = nil
    }

    private func fetchAndUpdate() {
        let symbol = selectedSymbol
        Task {
            do {
                let price = try await priceService.fetchPrice(symbol: symbol)
                await MainActor.run {
                    self.currentPrice = price
                    self.priceHistory.append((time: Date(), price: price))
                    if self.priceHistory.count > 60 {
                        self.priceHistory.removeFirst()
                    }
                    self.updateStatusBarTitle(price: price)
                    if self.popover.isShown {
                        self.popoverView.update(symbol: symbol, price: price, history: self.priceHistory)
                    }
                }
            } catch {
                await MainActor.run {
                    self.updateStatusTitle("\(PriceService.icon(for: symbol)) ❌")
                }
            }
        }
    }

    private func updateStatusTitle(_ text: String) {
        statusItem.button?.title = text
    }

    private func updateStatusBarTitle(price: Double) {
        let icon = PriceService.icon(for: selectedSymbol)
        let priceStr = PriceService.formatShortPrice(price)

        let attr = NSMutableAttributedString()

        // Icon
        attr.append(NSAttributedString(
            string: "\(icon) ",
            attributes: [
                .font: NSFont.systemFont(ofSize: 12, weight: .medium),
                .foregroundColor: NSColor.labelColor
            ]
        ))

        // Price
        attr.append(NSAttributedString(
            string: priceStr,
            attributes: [
                .font: NSFont.monospacedDigitSystemFont(ofSize: 12, weight: .semibold),
                .foregroundColor: NSColor.labelColor
            ]
        ))

        // P&L
        if UserDefaults.standard.bool(forKey: "showPnLInMenuBar") {
            let symbol = selectedSymbol
            let purchasePrices = UserDefaults.standard.dictionary(forKey: "purchasePrices") as? [String: Double] ?? [:]
            if let buyPrice = purchasePrices[symbol], buyPrice > 0 {
                let pnl = (price - buyPrice) / buyPrice * 100
                let isProfit = pnl >= 0
                let sign = isProfit ? "+" : ""
                let pnlStr = " \(sign)\(String(format: "%.1f", pnl))%"
                let pnlColor = isProfit ? NSColor.systemGreen : NSColor.systemRed

                // Separator dot
                attr.append(NSAttributedString(
                    string: " ·",
                    attributes: [
                        .font: NSFont.systemFont(ofSize: 10, weight: .light),
                        .foregroundColor: NSColor.tertiaryLabelColor
                    ]
                ))

                // P&L value
                attr.append(NSAttributedString(
                    string: pnlStr,
                    attributes: [
                        .font: NSFont.monospacedDigitSystemFont(ofSize: 11, weight: .semibold),
                        .foregroundColor: pnlColor
                    ]
                ))
            }
        }

        statusItem.button?.attributedTitle = attr
    }

    @objc private func refresh() {
        fetchAndUpdate()
    }

    @objc private func openSettings() {
        if settingsWindowController == nil {
            settingsWindowController = SettingsWindowController()
            settingsWindowController?.onSymbolChanged = { [weak self] in
                self?.fetchAndUpdate()
            }
        }
        settingsWindowController?.showWindow(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    @objc private func quit() {
        NSApplication.shared.terminate(nil)
    }
}

// MARK: - Settings Window

class SettingsWindowController: NSWindowController {
    var onSymbolChanged: (() -> Void)?
    private let popupButton = NSPopUpButton()
    private let priceField = NSTextField()
    private let pnlCheckbox = NSButton()

    convenience init() {
        let window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 320, height: 230),
            styleMask: [.titled, .closable],
            backing: .buffered,
            defer: false
        )
        window.title = "Settings"
        window.center()
        self.init(window: window)
        setupUI()
    }

    private func setupUI() {
        guard let contentView = window?.contentView else { return }

        // Cryptocurrency selector
        let symbolLabel = NSTextField(labelWithString: "Cryptocurrency:")
        symbolLabel.font = NSFont.systemFont(ofSize: 13)
        symbolLabel.frame = NSRect(x: 20, y: 180, width: 120, height: 22)
        contentView.addSubview(symbolLabel)

        let currentSymbol = UserDefaults.standard.string(forKey: "selectedSymbol") ?? "BTC"
        let items = PriceService.supportedCoins.map { "\($0.icon) \($0.symbol) - \($0.name)" }
        popupButton.addItems(withTitles: items)
        popupButton.font = NSFont.systemFont(ofSize: 13)
        popupButton.frame = NSRect(x: 20, y: 148, width: 280, height: 30)

        if let index = PriceService.supportedCoins.firstIndex(where: { $0.symbol == currentSymbol }) {
            popupButton.selectItem(at: index)
        }
        popupButton.target = self
        popupButton.action = #selector(symbolChanged)
        contentView.addSubview(popupButton)

        // Purchase price
        let buyLabel = NSTextField(labelWithString: "Purchase Price (USD):")
        buyLabel.font = NSFont.systemFont(ofSize: 13)
        buyLabel.frame = NSRect(x: 20, y: 112, width: 200, height: 22)
        contentView.addSubview(buyLabel)

        priceField.placeholderString = "e.g. 65000"
        priceField.font = NSFont.systemFont(ofSize: 13)
        priceField.frame = NSRect(x: 20, y: 82, width: 190, height: 24)

        let purchasePrices = UserDefaults.standard.dictionary(forKey: "purchasePrices") as? [String: Double] ?? [:]
        if let buyPrice = purchasePrices[currentSymbol] {
            priceField.stringValue = String(format: "%.2f", buyPrice)
        }
        contentView.addSubview(priceField)

        let confirmButton = NSButton(title: "Confirm", target: self, action: #selector(priceChanged))
        confirmButton.bezelStyle = .rounded
        confirmButton.font = NSFont.systemFont(ofSize: 13)
        confirmButton.frame = NSRect(x: 220, y: 80, width: 80, height: 28)
        contentView.addSubview(confirmButton)

        // P&L in menu bar toggle
        pnlCheckbox.setButtonType(.switch)
        pnlCheckbox.title = "Show P&L in menu bar"
        pnlCheckbox.font = NSFont.systemFont(ofSize: 13)
        pnlCheckbox.frame = NSRect(x: 18, y: 48, width: 280, height: 22)
        pnlCheckbox.state = UserDefaults.standard.bool(forKey: "showPnLInMenuBar") ? .on : .off
        pnlCheckbox.target = self
        pnlCheckbox.action = #selector(pnlToggleChanged)
        contentView.addSubview(pnlCheckbox)

        let note = NSTextField(labelWithString: "Price updates every 10 seconds from Binance")
        note.font = NSFont.systemFont(ofSize: 11)
        note.textColor = .secondaryLabelColor
        note.frame = NSRect(x: 20, y: 16, width: 280, height: 22)
        contentView.addSubview(note)
    }

    @objc private func symbolChanged() {
        let index = popupButton.indexOfSelectedItem
        let symbol = PriceService.supportedCoins[index].symbol
        UserDefaults.standard.set(symbol, forKey: "selectedSymbol")

        // Load purchase price for new symbol
        let purchasePrices = UserDefaults.standard.dictionary(forKey: "purchasePrices") as? [String: Double] ?? [:]
        if let buyPrice = purchasePrices[symbol] {
            priceField.stringValue = String(format: "%.2f", buyPrice)
        } else {
            priceField.stringValue = ""
        }

        onSymbolChanged?()
    }

    @objc private func priceChanged() {
        let symbol = UserDefaults.standard.string(forKey: "selectedSymbol") ?? "BTC"
        var purchasePrices = UserDefaults.standard.dictionary(forKey: "purchasePrices") as? [String: Double] ?? [:]

        if let price = Double(priceField.stringValue), price > 0 {
            purchasePrices[symbol] = price
        } else {
            purchasePrices.removeValue(forKey: symbol)
        }

        UserDefaults.standard.set(purchasePrices, forKey: "purchasePrices")
        onSymbolChanged?()
    }

    @objc private func pnlToggleChanged() {
        UserDefaults.standard.set(pnlCheckbox.state == .on, forKey: "showPnLInMenuBar")
        onSymbolChanged?()
    }
}
