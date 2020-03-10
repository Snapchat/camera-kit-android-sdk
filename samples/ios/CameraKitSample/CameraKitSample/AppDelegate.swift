//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import UIKit
import CameraKitReferenceUI

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    fileprivate let reachability: Reachability? = {
        let reachability = Reachability()
        return reachability
    }()

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        let viewController = CameraViewController()
        reachability?.delegate = viewController
        window?.rootViewController = viewController
        window?.makeKeyAndVisible()

        if reachability?.status != .connected {
            reachability?.startListening()
        }

        return true
    }
}

