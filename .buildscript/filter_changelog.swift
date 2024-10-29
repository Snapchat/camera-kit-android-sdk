#!/usr/bin/swift

import Foundation

if CommandLine.arguments.count < 3 {
    print("Usage: \(CommandLine.arguments[0]) <changelog_file> <platform> [<version>]")
    exit(1)
}

let filePath = CommandLine.arguments[1]
let platform = CommandLine.arguments[2].lowercased()
let version = CommandLine.arguments.count > 3 ? CommandLine.arguments[3] : nil

if !["ios", "android"].contains(platform) {
    print("Error: Platform must be either 'iOS' or 'Android'")
    exit(1)
}

guard let content = try? String(contentsOfFile: filePath, encoding: .utf8) else {
    print("Error: Unable to read the changelog file.")
    exit(1)
}

let oppositePlatform = platform == "ios" ? "android" : "ios"

let lines = content.components(separatedBy: .newlines)
var filteredLines1: [String] = []

func regexPatternForPlatform(_ platform: String) -> String {
    return "-\\s*\\*\\*\(platform):*\\*\\*\\s*"
}

var currentVersion: String? = nil
var validVersion = version == nil
var validPlatform = true

let pr = try Regex(regexPatternForPlatform(platform)).ignoresCase()
let opr = try Regex(regexPatternForPlatform(oppositePlatform)).ignoresCase()
let vr = try Regex("<a\\s+name=\"(.+)\">")

for line in lines {
    if let version {
        if let match = line.firstMatch(of: vr), let newVesrion = match[1].substring.map({ String($0) }) {
            if currentVersion == version {
                break
            }
            
            currentVersion = newVesrion
            validVersion = version == currentVersion
            continue
        }
    }
    
    if validVersion  {
        if version != nil, line.starts(with: "## ") {
            continue
        }

        if line.contains(pr) {
            validPlatform = true
        } else if line .contains(opr) {
            validPlatform = false
        } else if line.starts(with: "##") ||  line.starts(with: "<a"){
            validPlatform = true
        }
        if validPlatform {
            filteredLines1.append(line.replacing(pr, with: "- ").trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }
}

var filteredLines2: [String] = []
for (idx, line) in filteredLines1.enumerated() {
    
    if line.starts(with: "###") && idx + 1 < filteredLines1.count && !filteredLines1[idx + 1].starts(with: "-") {
        continue
    }
    
    if line.count == 0 && idx + 1 < filteredLines1.count  && filteredLines1[idx + 1].count == 0 {
        continue
    }
    
    filteredLines2.append(line)
}

let filteredChangelog = filteredLines2.joined(separator: "\n")
print(filteredChangelog)
