//  Copyright Snap Inc. All rights reserved.
//  CameraKitSample

import UIKit
import CameraKit

protocol LensPickerViewControllerDelegate: class {
    func lensPicker(_ viewController: LensPickerViewController, didSelect lens: Lens)
}

/// View controller to select sample lenses
class LensPickerViewController: UIViewController {
    weak var delegate: LensPickerViewControllerDelegate?

    private let lensHolder: LensHolder
    private var bundledLenses = [Lens]()
    var currentLens: Lens?

    let tableView: UITableView = {
        let tableView = UITableView(frame: .zero, style: .plain)
        tableView.translatesAutoresizingMaskIntoConstraints = false

        return tableView
    }()

    init(lensHolder: LensHolder, currentLens: Lens?) {
        self.lensHolder = lensHolder
        self.currentLens = currentLens
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        loadLenses()

        view.backgroundColor = .white

        navigationItem.title = "Lens Picker"
        let doneBarButtonItem = UIBarButtonItem(title: "Done", style: .done, target: self, action: #selector(dismissViewController(_:)))
        navigationItem.leftBarButtonItem = doneBarButtonItem

        tableView.delegate = self
        tableView.dataSource = self

        view.addSubview(tableView)

        setupVersionLabel()
        setupConstraints()
    }
    
    private func setupVersionLabel() {
        let label = UILabel()
        label.text = "v\(ApplicationInfo.version ?? "NA") (build \(ApplicationInfo.build ?? "NA"))"
        label.textAlignment = .center
        label.textColor = .lightGray
        label.frame.size.height = 44.0
        
        tableView.tableFooterView = label
    }

    private func setupConstraints() {
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    // MARK: dismiss

    @objc private func dismissViewController(_ sender: Any?) {
        dismiss(animated: true, completion: nil)
    }

    // MARK: load

    private func loadLenses() {
        lensHolder.getAvailableLenses { (lenses, _) in
            guard let lenses = lenses else {
                return
            }

            self.bundledLenses = lenses.sorted { $0.name ?? $0.id < $1.name ?? $1.id }
            self.tableView.reloadData()
        }
    }
}

// MARK: Table View

extension LensPickerViewController: UITableViewDelegate, UITableViewDataSource {
    private struct Constants {
        static let cellIdentifier = "LensPickerTableViewCellIdentifier"
    }

    func numberOfSections(in tableView: UITableView) -> Int {
        return 1
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return bundledLenses.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: Constants.cellIdentifier) ?? UITableViewCell(style: .subtitle, reuseIdentifier: Constants.cellIdentifier)

        let lens = bundledLenses[indexPath.row]
        cell.textLabel?.text = lens.name
        cell.detailTextLabel?.text = lens.id
        cell.accessoryType = lens.id == currentLens?.id ? .checkmark : .none

        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)

        let lens = bundledLenses[indexPath.row]
        currentLens = lens
        delegate?.lensPicker(self, didSelect: lens)
    }
}
