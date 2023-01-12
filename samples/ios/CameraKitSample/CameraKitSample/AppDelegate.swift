//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import UIKit
import SCSDKCameraKit
import SCSDKCameraKitReferenceUI
import SCSDKCreativeKit
// Reenable if using SwiftUI reference UI
//import SCSDKCameraKitReferenceSwiftUI
//import SwiftUI

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, SnapchatDelegate {

    private enum Constants {
        static let partnerGroupId = "5685839489138688"
    }

    var window: UIWindow?
    fileprivate var supportedOrientations: UIInterfaceOrientationMask = .allButUpsideDown

    let snapAPI = SCSDKSnapAPI()
    let cameraController : CustomizedCameraController
    var appConfigStorage = AppConfigStorage()
    
    override init() {
        if let customApiToken = appConfigStorage.apiToken, !customApiToken.isEmpty {
            // Use custom api token if exist. The api token can be updated by deeplink or debug UI.
            cameraController = CustomizedCameraController(
                sessionConfig: SessionConfig(
                    applicationID: appConfigStorage.applicationID , apiToken: customApiToken ))
        } else {
            // Use the default init which loads from plist
            cameraController = CustomizedCameraController()
        }
        super.init()
    }
    
    // This is how you configure properties for a CameraKit Session
    // Pass in applicationID and apiToken through a SessionConfig which will override the ones stored in the app's Info.plist
    // which is useful to dynamically update your apiToken in case it ever gets revoked.
    // let cameraController = CameraController(
    //    sessionConfig: SessionConfig(
    //        applicationID: "application_id_here", apiToken: "api_token_here"))

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        if let previousGroupIDs = appConfigStorage.groupIDs {
            cameraController.groupIDs = previousGroupIDs
        } else {
            cameraController.groupIDs = [SCCameraKitLensRepositoryBundledGroup, Constants.partnerGroupId]
        }
        
        // If you want to support sharing to Snapchat (via CreativeKit) you can set this delegate below.
        // Note that you need to make sure CreativeKit is set up correctly in your app, which includes
        // adding proper SnapKit app id in Info.plist (`SCSDKClientId`) and ensuring your app is either
        // approved in production and/or your Snapchat username is allowlisted in SnapKit dashboard.
        // See https://docs.snap.com/snap-kit/creative-kit/Tutorials/ios
        cameraController.snapchatDelegate = self
        let cameraViewController = CustomizedCameraViewController(cameraController: cameraController)
        cameraViewController.appOrientationDelegate = self
        window?.rootViewController = cameraViewController
        
//        If your application has a deployment target of 14.0 or higher, CameraKit Reference UI
//        supports a preview SwiftUI implementation.
//        let view = CameraView(cameraController: cameraController)
//        let cameraViewController = UIHostingController(rootView: view)
//        window?.rootViewController = cameraViewController
        
        window?.makeKeyAndVisible()
        
        return true
    }
    
    // Our app accepts custom deeplink with args of api token and groupIds on the fly for debugging purposes.
    // To test this feature, please open the safari browser and open with the following url:
    // camerakitsandbox://?apiToken=<replace with api token>&groupIds=5740388929241088,95d5f62c-abc8-4a8b-926c-5e52dd406afd
    func application(_ application: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey : Any] = [:] ) -> Bool {
        
        // Determine who sent the URL.
        let sendingAppID = options[.sourceApplication]
        print("source application = \(sendingAppID ?? "Unknown")")

        // Process the URL.
        guard let components = NSURLComponents(url: url, resolvingAgainstBaseURL: true),
            let _ = components.path,
            let params = components.queryItems else {
            print("Invalid URL or path missing")
            return false
        }

        if let apiToken = params.first(where: { $0.name == "apiToken" })?.value, !apiToken.isEmpty {
            print("overwriting with the apiToken = \(apiToken)")
            appConfigStorage.apiToken = apiToken
        }
        
        if let groupIdsInString = params.first(where: { $0.name == "groupIds" })?.value, !groupIdsInString.isEmpty {
            let groupIds = groupIdsInString.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            print("groupIds = \(groupIds)")
            appConfigStorage.groupIDs = groupIds
        }
        
        // As updating value of cache keys are completed, the application needs to be forced eto restart in order to take effect.
        exit(0)        
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
        snapAPI.startSending(content) { error in
            DispatchQueue.main.async {
                viewController.view.isUserInteractionEnabled = true
            }
            if let error = error {
                print("Failed to send content to Snapchat with error: \(error.localizedDescription)")
                return
            }
        }
    }

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return supportedOrientations
    }
}

// MARK: Helper Orientation Methods

extension AppDelegate: AppOrientationDelegate {

    func lockOrientation(_ orientation: UIInterfaceOrientationMask) {
        supportedOrientations = orientation
    }

    func unlockOrientation() {
        supportedOrientations = .allButUpsideDown
    }

}
