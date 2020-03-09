//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import UIKit
import AVFoundation
import CameraKit
import CameraKitReferenceUI

class CameraViewController: UIViewController {
    // Standard camera pipeline stuff
    fileprivate let session = AVCaptureSession()
    fileprivate var input: AVCaptureInput?
    fileprivate var position = AVCaptureDevice.Position.front {
        didSet {
            configureDevice()
        }
    }

    // CameraKit Classes
    fileprivate let previewView = PreviewView()
    fileprivate let pipView = PreviewView()
    fileprivate let cameraKit = Session()
    fileprivate lazy var lensHolder = LensHolder(repository: cameraKit.lenses.repository)
    fileprivate var currentLens: Lens?
    fileprivate lazy var reachability: Reachability? = {
        let reachability = Reachability()
        reachability?.delegate = self
        return reachability
    }()

    fileprivate let flipCameraButton: UIButton = {
        let button = UIButton(type: .custom)
        button.setImage(UIImage(named: "camera_flip"), for: .normal)
        button.translatesAutoresizingMaskIntoConstraints = false

        return button
    }()

    fileprivate let carouselView: CarouselView = {
        let view = CarouselView()
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    fileprivate let cameraButton: CameraButton = {
        let view = CameraButton()
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    fileprivate let messageView: MessageNotificationView = {
        let view = MessageNotificationView()
        view.alpha = 0.0
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        setup()
    }

}

// MARK: Lenses Setup

extension CameraViewController {

    fileprivate func setupLenses() {

        // Create a CameraKit input. AVSessionInput is an input that CameraKit provides that wraps up lens-specific
        // details of AVCaptureSession configuration (such as setting the pixel format).
        // You are still responsible for normal configuration of the session (adding the AVCaptureDevice, etc).
        let input = AVSessionInput(session: session)

        // Start the actual CameraKit session. Once the session is started, CameraKit will begin processing frames and
        // sending output. The lens processor (cameraKit.lenses.processor) will be instantiated at this point, and
        // you can start sending commands to it (such as applying/clearing lenses).
        cameraKit.start(with: input)

        // CameraKit has "outputs." When you send frames from an input (like the camera), CameraKit will process them
        // and output them. CameraKit provides a preview view that knows how to handle output automatically. You can
        // also add any other protocol-conforming instance as an output, such as a video recording output, or a
        // framerate counter.
        cameraKit.add(output: previewView)

        // CameraKit supports multiple outputs - an example of adding a 2nd output view.
        cameraKit.add(output: pipView)

        applyFirstLens()
    }

    fileprivate func applyFirstLens() {
        // Get all lenses from the repository and apply the first to the processor.
        // The lenses repository will query `lenses` folder bundled in the app.
        lensHolder.getAvailableLenses { (lenses, error) in
            guard let lenses = lenses, let lens = lenses.first else {
                print("Failed to get available lenses with error: \(String(describing: error))")
                return
            }

            self.carouselView.items = lenses.map { CarouselItem(imageUrl: $0.iconUrl) }

            self.applyLens(lens)
            self.showMessage(lens: lens)

            if error != nil {
                self.reachability?.startListening()
            }
        }
    }

    fileprivate func applyLens(_ lens: Lens) {
        cameraKit.lenses.processor?.apply(lens: lens) { success in
            if success {
                self.currentLens = lens
                print("\(lens.name ?? lens.id) Applied")
            } else {
                print("Lens failed to apply")
            }
        }
    }
}

// MARK: General Camera Setup

extension CameraViewController {

    fileprivate func setup() {
        setupPreview()
        setupPip()
        setupFlipCameraButton()
        setupCarousel()
        setupCameraRing()
        setupMessageView()
        setupNotifications()
        promptForAccessIfNeeded {
            self.setupSession()
            self.setupLenses()
        }
    }

    fileprivate func setupPreview() {
        view.addSubview(previewView)
        previewView.translatesAutoresizingMaskIntoConstraints = false
        view.addConstraints([
            previewView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            previewView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            previewView.topAnchor.constraint(equalTo: view.topAnchor),
            previewView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        let doubleTap = UITapGestureRecognizer(target: self, action: #selector(flip(sender:)))
        doubleTap.numberOfTapsRequired = 2
        previewView.addGestureRecognizer(doubleTap)
        previewView.automaticallyConfiguresTouchHandler = true
    }

    fileprivate func setupPip() {
        pipView.backgroundColor = .white
        pipView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(pipView)
        NSLayoutConstraint.activate([
            pipView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pipView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            pipView.heightAnchor.constraint(equalTo: view.heightAnchor, multiplier: 0.30),
            pipView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.30)
        ])
    }

    fileprivate func promptForAccessIfNeeded(completion: @escaping () -> Void) {
        guard AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined else { completion(); return }
        AVCaptureDevice.requestAccess(for: .video) { _ in
            completion()
        }
    }

    fileprivate func setupSession() {
        session.beginConfiguration()
        configureDevice()
        session.commitConfiguration()
        session.startRunning()
    }

    fileprivate func configureDevice() {
        if let existing = input {
            session.removeInput(existing)
        }
        let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position)!
        let input = try! AVCaptureDeviceInput(device: device)
        if session.canAddInput(input) {
            session.addInput(input)
            self.input = input
        }
    }
}

// MARK: Camera Flip

extension CameraViewController {
    fileprivate func setupFlipCameraButton() {
        flipCameraButton.addTarget(self, action: #selector(self.flip(sender:)), for: .touchUpInside)

        view.addSubview(flipCameraButton)

        NSLayoutConstraint.activate([
            flipCameraButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16.0),
            flipCameraButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8.0)
        ])
    }

    @objc fileprivate func flip(sender: UITapGestureRecognizer) {
        position = position == .back ? .front : .back
    }
}

// MARK: Carousel

extension CameraViewController: CarouselViewDelegate {

    fileprivate func setupCarousel() {
        carouselView.delegate = self
        view.addSubview(carouselView)
        NSLayoutConstraint.activate([
            carouselView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            carouselView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            carouselView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -32.0),
            carouselView.heightAnchor.constraint(equalToConstant: 62.0),
        ])
    }

    func carouselView(_ view: CarouselView, didSelect item: CarouselItem, at index: Int) {
        let lens = lensHolder.allLenses[index]
        applyLens(lens)
        showMessage(lens: lens)
    }
}

// MARK: Camera Ring

extension CameraViewController: UIGestureRecognizerDelegate {
    fileprivate func setupCameraRing() {
        view.addSubview(cameraButton)
        NSLayoutConstraint.activate([
            cameraButton.centerYAnchor.constraint(equalTo: carouselView.centerYAnchor),
            cameraButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
        ])
    }
}

// MARK: Messages

extension CameraViewController {
    fileprivate func setupMessageView() {
        view.addSubview(messageView)
        NSLayoutConstraint.activate([
            messageView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16.0),
            messageView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16.0),
        ])
    }

    fileprivate func showMessage(lens: Lens) {
        var numberOfLines = 1
        var text = lens.name ?? lens.id

        if lens.name != nil {
            text.append("\n\(lens.id)")
            numberOfLines += 1
        }

        showMessage(text: text, numberOfLines: numberOfLines)
    }

    fileprivate func showMessage(text: String, numberOfLines: Int) {
        messageView.layer.removeAllAnimations()
        messageView.label.text = text
        messageView.label.numberOfLines = numberOfLines
        messageView.alpha = 0.0

        UIView.animate(
            withDuration: 0.5,
            animations: {
                self.messageView.alpha = 1.0
            }
        ) { completed in
            if completed {
                UIView.animate(
                    withDuration: 0.5, delay: 1.0,
                    animations: {
                        self.messageView.alpha = 0.0
                    }, completion: nil)
            }
        }
    }
}

// MARK: Notifications

extension CameraViewController {

    fileprivate func setupNotifications() {
        NotificationCenter.default.addObserver(self, selector: #selector(self.appWillEnterForegroundNotification(_:)), name: UIApplication.willEnterForegroundNotification, object: nil)
    }

    @objc private func appWillEnterForegroundNotification(_ notification: Notification) {
        // SDK pauses/disables lens in background, so re-apply the lens when entering foreground
        guard let currentLens = currentLens else { return }

        applyLens(currentLens)
    }

}

// MARK: Reachability

extension CameraViewController: ReachabilityDelegate {
    func reachability(_ reachability: Reachability, didUpdateStatus status: Reachability.Status) {
        guard status == .connected else { return }

        applyFirstLens()
        reachability.stopListening()
    }
}
