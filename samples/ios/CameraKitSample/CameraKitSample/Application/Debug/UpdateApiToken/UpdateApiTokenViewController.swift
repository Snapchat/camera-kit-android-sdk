//
//  UpdateApiTokenViewController.swift
//  CameraKitSample
//
//  Created by Eric So  on 1/11/23.
//  Copyright © 2023 Snap. All rights reserved.
//

import UIKit

class UpdateApiTokenViewController: UIViewController {

    let textView = UITextView(frame: CGRect(x: 20.0, y: 0.0, width: 250.0, height: 400.0))
    var appConfigStorage = AppConfigStorage()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        let label = UILabel(frame: CGRect(x: 20.0, y: 0.0, width: 250.0, height: 50.0))
        label.textAlignment = .center
        label.text = NSLocalizedString("camera_kit_api_token_label", comment: "")
        
        textView.contentInsetAdjustmentBehavior = .automatic
        textView.textColor = UIColor.black
        textView.backgroundColor = UIColor.white
        if let token = self.appConfigStorage.apiToken, !token.isEmpty {
            textView.text = token
        }
        
        let saveButton = UIButton()
        saveButton.setTitle(NSLocalizedString("camera_kit_save_button", comment: ""), for: .normal)
        saveButton.translatesAutoresizingMaskIntoConstraints = false
        saveButton.titleLabel?.font = .boldSystemFont(ofSize: 18)
        if #available(iOS 13.0, *) {
            saveButton.setTitleColor(.link, for: .normal)
            saveButton.backgroundColor = .tertiarySystemBackground
        } else {
            saveButton.setTitleColor(.blue, for: .normal)
            saveButton.backgroundColor = .darkGray
        }
        
        let clearButton = UIButton()
        clearButton.setTitle(NSLocalizedString("camera_kit_clear_button", comment: ""), for: .normal)
        clearButton.translatesAutoresizingMaskIntoConstraints = false
        clearButton.titleLabel?.font = .boldSystemFont(ofSize: 18)
        if #available(iOS 13.0, *) {
            clearButton.setTitleColor(.link, for: .normal)
            clearButton.backgroundColor = .tertiarySystemBackground
        } else {
            clearButton.setTitleColor(.blue, for: .normal)
            clearButton.backgroundColor = .darkGray
        }
        
        let stackView = UIStackView(arrangedSubviews: [label, textView, saveButton, clearButton])
        stackView.axis = .vertical
        stackView.spacing = 20.0
        stackView.alignment = .fill
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)
        
        NSLayoutConstraint.activate([
          stackView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
          stackView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
          stackView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
          stackView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
        ])
        
        saveButton.addTarget(self, action: #selector(save), for: .touchUpInside)
        clearButton.addTarget(self, action: #selector(clear), for: .touchUpInside)
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
    }
    
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }
    
    @objc private func clear() {
        self.textView.text = ""
    }
    
    @objc private func save() {
        let dialogMessage = UIAlertController(title: NSLocalizedString("camera_kit_confirm_button", comment: ""), message: NSLocalizedString("camera_kit_save_api_token_confirmation_dialog", comment: ""), preferredStyle: .alert)

        // Create OK button with action handler
        let ok = UIAlertAction(title: NSLocalizedString("camera_kit_ok_button", comment: ""), style: .default, handler: { (action) -> Void in
            self.appConfigStorage.apiToken = self.textView.text
            // Force to quit this app so that the change will take effect.
            exit(0)
        })
        // Create Cancel button with action handler
        let cancel = UIAlertAction(title: NSLocalizedString("camera_kit_cancel_button", comment: ""), style: .cancel) { (action) -> Void in
        }
        //Add OK and Cancel button to an Alert object
        dialogMessage.addAction(ok)
        dialogMessage.addAction(cancel)
        // Present alert message to user
        self.present(dialogMessage, animated: true, completion: nil)
    }
}
