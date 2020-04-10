//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import UIKit
import CameraKitReferenceUI
import SCSDKCreativeKit

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, SnapchatDelegate {
    var window: UIWindow?

    let snapAPI = SCSDKSnapAPI()

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        let cameraViewController = CameraViewController()
        cameraViewController.snapchatDelegate = self
        window?.rootViewController = cameraViewController
        window?.makeKeyAndVisible()

        return true
    }

    func cameraKitViewController(_ viewController: UIViewController, openSnapchat screen: SnapchatScreen) {
        switch screen {
        case .profile, .lens(_):
            // not supported yet in creative kit (1.4.2), should be added in next version
            break
        case .photo(let image):
            let photo = SCSDKSnapPhoto(image: image)
            let content = SCSDKPhotoSnapContent(snapPhoto: photo)
            sendSnapContent(content, viewController: viewController)
        case .video(let url):
            let video = SCSDKSnapVideo(videoUrl: url)
            let content = SCSDKVideoSnapContent(snapVideo: video)
            sendSnapContent(content, viewController: viewController)
        }
    }

    private func sendSnapContent(_ content: SCSDKSnapContent, viewController: UIViewController) {
        viewController.view.isUserInteractionEnabled = false
        snapAPI.startSending(content) { _ in
            viewController.view.isUserInteractionEnabled = true
        }
    }
}

