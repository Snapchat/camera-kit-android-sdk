//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import Foundation
import CameraKit

/// Class to handle getting, caching, etc. lenses from repository so logic isn't in controller
class LensHolder {
    private struct Constants {
        static let groupId = "1"
    }

    let repository: LensRepository

    var bundledLenses = [Lens]()
    var availableLenses = [Lens]()
    var allLenses: [Lens] {
        return bundledLenses + availableLenses
    }

    init(repository: LensRepository) {
        self.repository = repository
    }

    func getAvailableLenses(completion: @escaping ((_ lenses: [Lens]?, _ error: Error?) -> Void)) {
        guard availableLenses.isEmpty || bundledLenses.isEmpty else {
            completion(allLenses, nil)
            return
        }

        let group = DispatchGroup()
        var errors: [Error?] = []

        group.enter()
        repository.availableLenses(groupID: SCCameraKitLensRepositoryBundledGroup) { (lens, error) in
            errors.append(error)
            self.bundledLenses = (lens ?? []).sorted { $0.name ?? $0.id < $1.name ?? $1.id }
            group.leave()
        }

        group.enter()
        repository.availableLenses(groupID: Constants.groupId) { (lens, error) in
            errors.append(error)
            self.availableLenses = (lens ?? []).sorted { $0.name ?? $0.id < $1.name ?? $1.id }
            group.leave()
        }

        group.notify(queue: .main) {
            let anyError = errors.compactMap { $0 }.first
            completion(self.allLenses, anyError)
        }
    }

    func lens(before lens: Lens, completion: @escaping ((_ lens: Lens?) -> Void)) {
        getAvailableLenses { (lenses, _) in
            guard let lenses = lenses,
                let index = lenses.firstIndex(where: { $0.id == lens.id }) else {
                completion(nil)
                return
            }

            if index == 0 {
                completion(lenses.last)
            } else {
                completion(lenses[lenses.index(before: index)])
            }
        }
    }

    func lens(after lens: Lens, completion: @escaping ((_ lens: Lens?) -> Void)) {
        getAvailableLenses { (lenses, _) in
            guard let lenses = lenses,
                let index = lenses.firstIndex(where: { $0.id == lens.id }) else {
                completion(nil)
                return
            }

            if index == lenses.count - 1 {
                completion(lenses.first)
            } else {
                completion(lenses[lenses.index(after: index)])
            }
        }
    }
}
