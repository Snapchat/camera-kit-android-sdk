//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import UIKit
import AVFoundation
import CameraKit

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
    fileprivate let cameraKit = CameraKit()
    fileprivate lazy var lensHolder = LensHolder(repository: cameraKit.lenses.repository)
    fileprivate var currentLens: Lens?

    fileprivate let lensPickerButton = UIButton(type: .custom)
    fileprivate let prevLensButton = UIButton(type: .custom)
    fileprivate let nextLensButton = UIButton(type: .custom)
    fileprivate let flipCameraButton: UIButton = {
        let button = UIButton(type: .custom)
        button.setImage(UIImage(named: "camera_flip"), for: .normal)
        button.translatesAutoresizingMaskIntoConstraints = false

        return button
    }()
    lazy var lensButtonStackView: UIStackView = {
        let stackView = UIStackView(arrangedSubviews: [prevLensButton, lensPickerButton, nextLensButton])
        stackView.alignment = .center
        stackView.axis = .horizontal
        stackView.distribution = .fill
        stackView.spacing = 8.0
        stackView.translatesAutoresizingMaskIntoConstraints = false

        return stackView
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

        applyFirstLens()
    }

    fileprivate func applyFirstLens() {
        // Get all lenses from the repository and apply the first to the processor.
        // The lenses repository will query `lenses` folder bundled in the app.
        lensHolder.getAvailableLenses { (lenses, error) in
            guard let lens = lenses?.first else {
                print("Failed to get available lenses with error: \(String(describing: error))")
                return
            }

            self.applyLens(lens)
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
        setupFlipCameraButton()
        setupLensPicker()
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

// MARK: Lens Picker

extension CameraViewController: LensPickerViewControllerDelegate {

    private struct Constants {
        static let lensPickerImage = "lens_preview_button"
        static let nextArrowImage = "arrow_right"
        static let prevArrowImage = "arrow_left"
    }

    fileprivate func setupLensPicker() {
        lensPickerButton.setImage(UIImage(named: Constants.lensPickerImage), for: .normal)
        lensPickerButton.addTarget(self, action: #selector(self.showLensPicker(_:)), for: .touchUpInside)

        prevLensButton.setImage(UIImage(named: Constants.prevArrowImage), for: .normal)
        prevLensButton.addTarget(self, action: #selector(self.showPrevLens(_:)), for: .touchUpInside)

        nextLensButton.setImage(UIImage(named: Constants.nextArrowImage), for: .normal)
        nextLensButton.addTarget(self, action: #selector(self.showNextLens(_:)), for: .touchUpInside)

        view.addSubview(lensButtonStackView)

        NSLayoutConstraint.activate([
            lensButtonStackView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -32.0),
            lensButtonStackView.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])
    }

    // MARK: Actions

    @objc private func showLensPicker(_ sender: UIButton) {
        let viewController = LensPickerViewController(lensHolder: lensHolder, currentLens: currentLens)
        viewController.delegate = self
        let navController = UINavigationController(rootViewController: viewController)
        present(navController, animated: true, completion: nil)
    }

    @objc private func showPrevLens(_ sender: UIButton) {
        guard let curr = currentLens else {
            applyFirstLens()
            return
        }

        lensHolder.lens(before: curr) { lens in
            guard let lens = lens else { return }
            self.applyLens(lens)
        }
    }

    @objc private func showNextLens(_ sender: UIButton) {
        guard let curr = currentLens else {
            applyFirstLens()
            return
        }

        lensHolder.lens(after: curr) { lens in
            guard let lens = lens else { return }
            self.applyLens(lens)
        }
    }

    // MARK: Lens Picker Delegate

    func lensPicker(_ viewController: LensPickerViewController, didSelect lens: Lens) {
        applyLens(lens)
        viewController.dismiss(animated: true, completion: nil)
    }
}
