import Foundation

struct ReleaseInfo { let version: String; let title: String; let notes: String; let url: String }

enum Updater {
    static let repo = "za7ef/home-heroes"

    static var localVersion: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0" }
    static var localBuild: String { Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0" }

    /** آخر إصدار من صفحة الإصدارات العامة في GitHub */
    static func fetchLatest() async throws -> ReleaseInfo {
        var req = URLRequest(url: URL(string: "https://api.github.com/repos/\(repo)/releases/latest")!)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.timeoutInterval = 8
        let (data, resp) = try await URLSession.shared.data(for: req)
        if let h = resp as? HTTPURLResponse, h.statusCode != 200 {
            throw NSError(domain: "GitHub", code: h.statusCode, userInfo: [NSLocalizedDescriptionKey: "رد GitHub برمز \(h.statusCode)"])
        }
        let o = (try JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        let tag = (o["tag_name"] as? String ?? "").replacingOccurrences(of: "v", with: "")
        return ReleaseInfo(version: tag, title: o["name"] as? String ?? tag, notes: o["body"] as? String ?? "", url: o["html_url"] as? String ?? "")
    }

    static func isNewer(_ remote: String, _ local: String) -> Bool {
        let r = remote.split(separator: ".").map { Int($0) ?? 0 }
        let l = local.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(r.count, l.count) {
            let a = i < r.count ? r[i] : 0, b = i < l.count ? l[i] : 0
            if a != b { return a > b }
        }
        return false
    }
}
