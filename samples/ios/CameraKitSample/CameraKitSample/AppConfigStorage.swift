//
//  AppConfigStorage.swift
//  CameraKitSample
//
//  Created by Eric So  on 1/11/23.
//  Copyright © 2023 Snap. All rights reserved.
//

import Foundation

// Manages all sample app config which storage in the disk.
struct AppConfigStorage {
    
    enum Constants {
        static let namespace = "com.snap.camerakit.sample."
        static let lensGroupIDsKey = namespace + "lensGroupIDsKey"
        static let customApiTokenKey = namespace + "customApiTokenKey"
        static let cameraKitClientIDPropertyName = "SCCameraKitClientID"
    }
    
    var apiToken: String? {
        get {
            return UserDefaults.standard.object(forKey: Constants.customApiTokenKey) as? String
        }
        set {
            UserDefaults.standard.set(newValue, forKey: Constants.customApiTokenKey)
        }
    }
    
    var groupIDs: [String]? {
        get {
            return UserDefaults.standard.object(forKey: Constants.lensGroupIDsKey) as? [String]
        }
        set {
            UserDefaults.standard.set(newValue, forKey: Constants.lensGroupIDsKey)
        }
    }
    
    var applicationID: String {
        get  {
            return Bundle.main.object(forInfoDictionaryKey: Constants.cameraKitClientIDPropertyName) as! String
        }
    }
    
    func resetAll() {
        UserDefaults.standard.removeObject(forKey: Constants.customApiTokenKey)
        UserDefaults.standard.removeObject(forKey: Constants.lensGroupIDsKey)
    }
}
