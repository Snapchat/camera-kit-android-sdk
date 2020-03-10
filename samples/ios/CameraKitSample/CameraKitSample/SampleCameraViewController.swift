//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import UIKit
import AVFoundation
import CameraKit
import CameraKitReferenceUI

// MARK: Reachability

extension CameraViewController: ReachabilityDelegate {
    func reachability(_ reachability: Reachability, didUpdateStatus status: Reachability.Status) {
        guard status == .connected else { return }

        applyFirstLens()
        reachability.stopListening()
    }
}
