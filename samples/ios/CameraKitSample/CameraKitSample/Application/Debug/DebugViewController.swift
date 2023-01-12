//  DebugViewController.swift
//  CameraKitSample

import UIKit
import SCSDKCameraKitReferenceUI

class DebugViewController: UIViewController {

    var updateLensGroupButton: UIButton = {
        let button = UIButton()
        button.setTitle(NSLocalizedString("camera_kit_update_lens_group_button", comment: ""), for: .normal)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.titleLabel?.font = .boldSystemFont(ofSize: 18)
        if #available(iOS 13.0, *) {
            button.setTitleColor(.link, for: .normal)
            button.backgroundColor = .tertiarySystemBackground
        } else {
            button.setTitleColor(.blue, for: .normal)
            button.backgroundColor = .darkGray
        }
        return button
    }()
    
    var updateApiTokenButton: UIButton = {
        let button = UIButton()
        button.setTitle(NSLocalizedString("camera_kit_update_apitoken_button", comment: ""), for: .normal)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.titleLabel?.font = .boldSystemFont(ofSize: 18)
        if #available(iOS 13.0, *) {
            button.setTitleColor(.link, for: .normal)
            button.backgroundColor = .tertiarySystemBackground
        } else {
            button.setTitleColor(.blue, for: .normal)
            button.backgroundColor = .darkGray
        }
        return button
    }()
    
    var resetValueButton: UIButton = {
        let button = UIButton()
        button.setTitle(NSLocalizedString("camera_kit_reset_button", comment: ""), for: .normal)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.titleLabel?.font = .boldSystemFont(ofSize: 18)
        if #available(iOS 13.0, *) {
            button.setTitleColor(.link, for: .normal)
            button.backgroundColor = .tertiarySystemBackground
        } else {
            button.setTitleColor(.blue, for: .normal)
            button.backgroundColor = .darkGray
        }
        return button
    }()

    /// View controller for updating lens groups
    lazy var updateLensGroupViewController = UpdateLensGroupViewController(cameraController: cameraController, carouselView: carouselView)
    lazy var updateApiTokenViewController = UpdateApiTokenViewController()

    let cameraController: CameraController
    let carouselView: CarouselView

    init(cameraController: CameraController, carouselView: CarouselView) {
        self.cameraController = cameraController
        self.carouselView = carouselView
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setUp()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if #available(iOS 15.0, *) {
            navigationController?.sheetPresentationController?.animateChanges {
                navigationController?.sheetPresentationController?.detents = [.medium()]
            }
        }
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: NSLocalizedString("camera_kit_done_button", comment: ""), style: .done, target: self, action: #selector(dismissButton))
    }

    private func setUp() {
        navigationItem.title = NSLocalizedString("camera_kit_debug_nav_title", comment: "")
        if #available(iOS 13.0, *) {
            view.backgroundColor = .secondarySystemBackground
        } else {
            view.backgroundColor = .lightGray
        }
        navigationController?.view.backgroundColor = view.backgroundColor
        
        let stackView = UIStackView(arrangedSubviews: [updateLensGroupButton,updateApiTokenButton, resetValueButton])
        stackView.axis = .vertical
        stackView.spacing = 20.0
        stackView.alignment = .fill
        stackView.distribution = .fillEqually
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)

        NSLayoutConstraint.activate([
          stackView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
          stackView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
          stackView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
          stackView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
        ])

        updateLensGroupButton.addTarget(self, action: #selector(updateLensGroupButtonTapped), for: .touchUpInside)
        
        updateApiTokenButton.addTarget(self, action: #selector(updateApiTokenButtonTapped), for: .touchUpInside)
        
        resetValueButton.addTarget(self, action: #selector(resetValues), for: .touchUpInside)
    }
    
    @objc private func resetValues() {
        let dialogMessage = UIAlertController(title: NSLocalizedString("camera_kit_confirm_button", comment: ""), message: NSLocalizedString("camera_kit_reset_config_confirmation_dialog", comment: ""), preferredStyle: .alert)

        // Create OK button with action handler
        let ok = UIAlertAction(title: NSLocalizedString("camera_kit_ok_button", comment: ""), style: .default, handler: { (action) -> Void in
            let appConfigStorage = AppConfigStorage()
            appConfigStorage.resetAll()
            // Force to quit this app so that the change will take effect.
            exit(0)
        })
        // Create Cancel button with action handlder
        let cancel = UIAlertAction(title: NSLocalizedString("camera_kit_cancel_button", comment: ""), style: .cancel) { (action) -> Void in
        }
        //Add OK and Cancel button to an Alert object
        dialogMessage.addAction(ok)
        dialogMessage.addAction(cancel)
        // Present alert message to user
        self.present(dialogMessage, animated: true, completion: nil)
    }

    @objc func updateLensGroupButtonTapped(_ sender: UIButton) {
        navigationController?.pushViewController(updateLensGroupViewController, animated: true)
    }
    
    @objc func updateApiTokenButtonTapped(_ sender: UIButton) {
        navigationController?.pushViewController(updateApiTokenViewController, animated: true)
    }
    
    @objc func dismissButton(_ sender: UIButton) {
        dismiss(animated: true)
    }
}
