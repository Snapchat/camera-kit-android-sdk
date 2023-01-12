//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import XCTest

class CameraKitSampleUITests: XCTestCase {

    override func setUp() {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
    }

    override func tearDown() {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    func testExample() {
        // UI tests must launch the application that they test.
        let app = XCUIApplication()
        app.launch()

        // Use recording to get started writing UI tests.
        // Use XCTAssert and related functions to verify your tests produce the correct results.
    }

    func testLaunchPerformance() {
        if #available(macOS 10.15, iOS 13.0, tvOS 13.0, *) {
            // This measures how long it takes to launch your application.
            measure(metrics: [XCTOSSignpostMetric.applicationLaunch]) {
                XCUIApplication().launch()
            }
        }
    }
    
    func testDeeplinkFromSafari() {
        let app = XCUIApplication()
        app.launch()
        // Open Safari, wait for it to launch, and visit the Swifter server.
        let safari = XCUIApplication(bundleIdentifier: "com.snap.camerakit.sample")
        safari.launch()
        // Launch Safari and deeplink back to our app
        openFromSafari("camerakitsandbox://?apiToken=eyJhbGciOiJIUzI1NiIsImtpZCI6IkNhbnZhc1MyU0hNQUNQcm9kIiwidHlwIjoiSldUIn0.eyJhdWQiOiJjYW52YXMtY2FudmFzYXBpIiwiaXNzIjoiY2FudmFzLXMyc3Rva2VuIiwibmJmIjoxNjM4NDc0OTE0LCJzdWIiOiJiMjFjZmIyNy0wNGU5LTRiNzctYmQxYS0xNDM1NTIyZmI0NzF-U1RBR0lOR34zMzQxMmZkZC0zMDA3LTRiMTgtOGE5OC1hNjAzZTY4MzJhMmEifQ.BBVRgyVT4I_Z_qevzAqVwkWNXZGMHQ0s4tRJst9qfwE&groupIds=5740388929241088,95d5f62c-abc8-4a8b-926c-5e52dd406afd")
        // Make sure Safari properly switched back to our app before asserting
        XCTAssert(app.wait(for: .runningForeground, timeout: 20))
    }
    
    private func openFromSafari(_ urlString: String) {
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        safari.launch()
        // Make sure Safari is really running before asserting
        XCTAssert(safari.wait(for: .runningForeground, timeout: 5))
        // Type the deeplink and execute it
        let firstLaunchContinueButton = safari.buttons["Continue"]
        if firstLaunchContinueButton.exists {
            firstLaunchContinueButton.tap()
        }
        
        safari.textFields["Address"].tap()
        safari.textFields["Address"].typeText(urlString)
        safari.keyboards.buttons["Go"].tap()
        safari.buttons["Open"].tap()
    }
}
