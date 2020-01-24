//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import Foundation
import CameraKit

/// Class to handle getting, caching, etc. lenses from repository so logic isn't in controller
class LensHolder {
    let repository: LensRepository

    private var availableLenses = [Lens]()

    init(repository: LensRepository) {
        self.repository = repository
    }

    func getAvailableLenses(completion: @escaping ((_ lenses: [Lens]?, _ error: Error?) -> Void)) {
        guard availableLenses.isEmpty else {
            completion(availableLenses, nil)
            return
        }

        repository.availableLenses { (lens, error) in
            guard let lens = lens else {
                completion(nil, error)
                return
            }

            self.availableLenses = lens.sorted { $0.name ?? $0.id < $1.name ?? $1.id }
            completion(self.availableLenses, nil)
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
