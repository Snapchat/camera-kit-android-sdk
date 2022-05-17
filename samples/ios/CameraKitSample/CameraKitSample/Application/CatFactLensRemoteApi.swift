//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import Foundation
import SCSDKCameraKit

class CatFactRemoteApiServiceProvider: NSObject, LensRemoteApiServiceProvider {

    var supportedApiSpecIds: Set<String> = ["03d765c5-20bd-4495-9a27-30629649cf57"]

    func remoteApiService(for lens: Lens) -> LensRemoteApiService {
        return CatFactRemoteApiService()
    }
}

class CatFactRemoteApiService: NSObject, LensRemoteApiService {

    private enum Constants {
        static let scheme = "https"
        static let host = "catfact.ninja"
    }

    private let urlSession: URLSession = .shared

    func processRequest(
        _ request: LensRemoteApiRequest,
        responseHandler: @escaping (LensRemoteApiServiceCallStatus, LensRemoteApiResponseProtocol) -> Void
    ) -> LensRemoteApiServiceCall {
        guard let url = url(request: request) else {
            return IgnoredRemoteApiServiceCall()
        }

        let task = urlSession.dataTask(with: url) { data, urlResponse, error in
            let apiResponse = LensRemoteApiResponse(
                request: request,
                status: error != nil ? .badRequest : .success,
                metadata: [:],
                body: data)

            responseHandler(.answered, apiResponse)
        }

        task.resume()

        return URLRequestRemoteApiServiceCall(task: task)
    }

    private func url(request: LensRemoteApiRequest) -> URL? {
        var components = URLComponents()
        components.host = Constants.host
        components.path = "/" + request.endpointId
        components.scheme = Constants.scheme
        return components.url
    }

}

class URLRequestRemoteApiServiceCall: NSObject, LensRemoteApiServiceCall {

    let task: URLSessionDataTask

    let status: LensRemoteApiServiceCallStatus = .ongoing

    init(task: URLSessionDataTask) {
        self.task = task
        super.init()
    }

    func cancelRequest() {
        task.cancel()
    }

}

class IgnoredRemoteApiServiceCall: NSObject, LensRemoteApiServiceCall {
    let status: LensRemoteApiServiceCallStatus = .ignored

    func cancelRequest() {
        // no-op
    }
}
