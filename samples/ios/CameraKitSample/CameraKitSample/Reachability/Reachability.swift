//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import Foundation
import SystemConfiguration

protocol ReachabilityDelegate: class {
    func reachability(_ reachability: Reachability, didUpdateStatus status: Reachability.Status)
}

class Reachability {
    enum Status {
        case connected, notConnected

        init(_ flags: SCNetworkReachabilityFlags) {
            if flags.contains(.reachable) {
                self = .connected
            } else {
                self = .notConnected
            }
        }
    }

    weak var delegate: ReachabilityDelegate?

    private let reachability: SCNetworkReachability
    public let reachabilityQueue = DispatchQueue(label: "com.snap.CameraKitSample.reachabilityQueue")

    private var isListening = false

    var flags: SCNetworkReachabilityFlags? {
        var flags = SCNetworkReachabilityFlags()
        return SCNetworkReachabilityGetFlags(reachability, &flags) ? flags : nil
    }

    var status: Status {
        return flags.map(Status.init) ?? .notConnected
    }

    public convenience init?() {
        var zero = sockaddr()
        zero.sa_len = UInt8(MemoryLayout<sockaddr>.size)
        zero.sa_family = sa_family_t(AF_INET)

        guard let reachability = SCNetworkReachabilityCreateWithAddress(nil, &zero) else { return nil }

        self.init(reachability: reachability)
    }

    private init(reachability: SCNetworkReachability) {
        self.reachability = reachability
    }

    deinit {
        stopListening()
    }

    @discardableResult
    func startListening() -> Bool {
        guard !isListening else { return true }

        stopListening()
        var context = SCNetworkReachabilityContext(version: 0, info: Unmanaged.passUnretained(self).toOpaque(), retain: nil, release: nil, copyDescription: nil)

        let queueAdded = SCNetworkReachabilitySetDispatchQueue(reachability, reachabilityQueue)
        let callbackAdded = SCNetworkReachabilitySetCallback(reachability, { (_, flags, info) in
            guard let info = info else { return }
            let instance = Unmanaged<Reachability>.fromOpaque(info).takeUnretainedValue()
            instance.updateStatus(flags: flags)
        }, &context)

        isListening = queueAdded && callbackAdded
        return isListening
    }

    func stopListening() {
        SCNetworkReachabilitySetCallback(reachability, nil, nil)
        SCNetworkReachabilitySetDispatchQueue(reachability, nil)
        isListening = false
    }

    private func updateStatus(flags: SCNetworkReachabilityFlags) {
        delegate?.reachability(self, didUpdateStatus: .init(flags))
    }
}
