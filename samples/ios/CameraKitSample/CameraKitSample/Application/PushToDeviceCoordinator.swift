//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import Foundation

#if ENABLE_PUSH_TO_DEVICE
import SCSDKCameraKit
import SCSDKCameraKitLoginKitAuth
import SCSDKCameraKitPushToDeviceExtension
import SCSDKCameraKitReferenceUI

class PushToDeviceCoordinator: NSObject {
    let tokenProvider: LoginKitAccessTokenProvider
    var pushToDevice: PushToDevice
    let cameraViewController: CameraViewController
    
    init(cameraController: CameraViewController) {
        self.cameraViewController = cameraController
        tokenProvider = LoginKitAccessTokenProvider()
        pushToDevice = PushToDevice(
            cameraKitSession: cameraController.cameraController.cameraKit,
            tokenProvider: tokenProvider
        )
        super.init()
        setup()
    }
    
    func setup() {
        cameraViewController.cameraView.cameraActionsView.buttonStackView.addArrangedSubview(devicePairingButton)
        devicePairingButton.addTarget(self, action: #selector(self.pairingButtonTapped(sender:)), for: .touchUpInside)
        cameraViewController.cameraController.groupIDs = ["PushToDeviceGroup"]
    }
    
    fileprivate let devicePairingButton: UIButton = {
        let button = UIButton(type: .custom)
        button.accessibilityValue = OtherElements.pairingButton.rawValue
        if #available(iOS 14.0, *) {
            button.setImage(UIImage(systemName: "link.icloud"), for: .normal)
        }
        button.tintColor = .white
        return button
    }()
    
    @objc fileprivate func pairingButtonTapped(sender: Any) {
        pushToDevice.delegate = self
        pushToDevice.initiatePairing()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

}

extension PushToDeviceCoordinator: PushToDeviceDelegate {
    func pushToDeviceDidAcquireAuthToken(_ pushToDevice: PushToDeviceProtocol) {
        print("\(NSDate()) did acquire auth token")
        DispatchQueue.main.async {
            self.cameraViewController.cameraView.showMessage(text: "Point camera at a Snapcode from Lens Studio", numberOfLines: 2)
        }
    }
    
    func pushToDeviceDidScanSnapcode(_ pushToDevice: PushToDeviceProtocol) {
        print("\(NSDate()) pushToDeviceDidScanSnapcode")
        DispatchQueue.main.async {
            self.cameraViewController.cameraView.showMessage(text: "Pairing initiated", numberOfLines: 1)
            self.cameraViewController.cameraView.activityIndicator.startAnimating()
        }
    }
    
    func pushToDeviceComplete(_ pushToDevice: PushToDeviceProtocol) {
        print("\(NSDate()) pushToDeviceComplete")
        DispatchQueue.main.async {
            self.cameraViewController.cameraView.showMessage(text: "Pairing Complete. Push Lens from Lens Studio", numberOfLines: 2)
            self.cameraViewController.cameraView.activityIndicator.stopAnimating()
        }
    }
    
    func didReceiveLens(_ pushToDevice: PushToDeviceProtocol) {
        print("\(NSDate()) didReceiveLens")
        DispatchQueue.main.async {
            self.cameraViewController.cameraView.showMessage(text: "Lens Received", numberOfLines: 1)
            self.devicePairingButton.tintColor = .green
            self.cameraViewController.cameraView.activityIndicator.stopAnimating()
        }
    }
    
    func pushToDevice(_ pushToDevice: PushToDeviceProtocol, failedToScanSnapcodeWithError error: Error) {
        print("\(NSDate()) failedToScanSnapcodeWithError \(error)")
        DispatchQueue.main.async {
            self.cameraViewController.cameraView.showMessage(text: "Scanning Snapcode failed", numberOfLines: 1)
            self.devicePairingButton.tintColor = .red
            self.cameraViewController.cameraView.activityIndicator.stopAnimating()
        }
    }
    
    func didApplyLens(_ pushToDevice: PushToDeviceProtocol) {
        print("\(NSDate()) didApplyLens")
        DispatchQueue.main.async {
            self.devicePairingButton.tintColor = .green
        }
    }
    
    func pushToDevice(_ pushToDevice: PushToDeviceProtocol, failedToAcquireAuthToken error: Error) {
        print("\(NSDate()) failedToAcquireAuthToken \(error)")
        DispatchQueue.main.async {
            self.cameraViewController.cameraView.showMessage(text: "Authorization failed", numberOfLines: 1)
            self.devicePairingButton.tintColor = .red
            self.cameraViewController.cameraView.activityIndicator.stopAnimating()
        }
    }
    
    func pushToDevice(_ pushToDevice: PushToDeviceProtocol, didReceiveLensPushError error: Error) {
        print("\(NSDate()) didReceiveLensPushError \(error)")
        DispatchQueue.main.async {
            self.cameraViewController.cameraView.showMessage(text: "Lens Push Error", numberOfLines: 1)
            self.devicePairingButton.tintColor = .red
            self.cameraViewController.cameraView.activityIndicator.stopAnimating()
        }
    }
}
#endif
