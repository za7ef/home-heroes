import SwiftUI

enum Screen: Equatable {
    case home
    case room(String)
    case kid(String)
    case rewards(String)
    case parent
    case editChores(String)
    case editRewards
    case about
    case settings
    case text(String, String)
}

final class Nav: ObservableObject {
    @Published var screen: Screen = .home
    @Published var celebrating: Kid? = nil
    @Published var toast: String? = nil
    @Published var gate = false
    /** الشاشة التي يرجع لها زر الرجوع في الإعدادات */
    var settingsBack: Screen = .home

    func show(_ s: Screen) { withAnimation(.easeInOut(duration: 0.2)) { screen = s } }

    func flash(_ s: String) {
        toast = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) { [weak self] in
            if self?.toast == s { self?.toast = nil }
        }
    }
}

@main
struct HomeHeroesApp: App {
    @StateObject private var store = Store()
    @StateObject private var nav = Nav()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(nav)
                .environment(\.layoutDirection, .rightToLeft)
                .onAppear { store.pruneOld(); Sound.shared.startMusic() }
        }
    }
}

let bgColor = Color(hex: 0xFFF4D6)
let orange = Color(hex: 0xE8590C)
let green = Color(hex: 0x2F9E44)
let gray = Color(hex: 0x868E96)
let blue = Color(hex: 0x1C7ED6)
let red = Color(hex: 0xE03131)

struct RootView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var update: ReleaseInfo? = nil

    var body: some View {
        ZStack {
            bgColor.ignoresSafeArea()
            Group {
                switch nav.screen {
                case .home: HomeView()
                case .room(let id): RoomView(room: Rooms.byId(id)).id(id + "\(store.tick)")
                case .kid(let id): KidView(kid: Family.byId(id))
                case .rewards(let id): RewardsView(kid: Family.byId(id))
                case .parent: ParentView()
                case .editChores(let id): EditChoresView(kid: Family.byId(id))
                case .editRewards: EditRewardsView()
                case .about: AboutView()
                case .settings: SettingsView()
                case .text(let t, let b): TextPage(title: t, body: b)
                }
            }
            if let k = nav.celebrating { CelebrationView(kid: k) }
            if nav.gate { PinPrompt() }
            if let t = nav.toast {
                VStack {
                    Spacer()
                    Text(t).font(.headline).foregroundColor(.white)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .background(Capsule().fill(Color.black.opacity(0.8)))
                        .padding(.bottom, 30)
                }.transition(.opacity)
            }
        }
        .task { await checkUpdate() }
        .alert("تحديث جديد متوفر", isPresented: Binding(get: { update != nil }, set: { if !$0 { update = nil } })) {
            Button("لاحقًا", role: .cancel) {
                if let u = update { store.skippedVersion = u.version }
                update = nil
            }
            Button("تحديث") {
                if let u = update, let url = URL(string: u.url) { UIApplication.shared.open(url) }
                update = nil
            }
        } message: {
            Text("الإصدار \(update?.version ?? "")\n\nما الجديد:\n\(update?.notes ?? "")")
        }
    }

    private func checkUpdate() async {
        guard let info = try? await Updater.fetchLatest() else { return }
        if Updater.isNewer(info.version, Updater.localVersion), store.skippedVersion != info.version {
            update = info
        }
    }
}

/** شاشة عادية بعنوان وزر رجوع ومحتوى قابل للتمرير */
struct Page<Content: View>: View {
    let title: String
    let back: Screen?
    @ViewBuilder let content: () -> Content
    @EnvironmentObject var nav: Nav

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if let b = back {
                    Btn("→ رجوع", gray) { nav.show(b) }
                }
                Text(title).font(.system(size: 26, weight: .bold)).foregroundColor(orange).frame(maxWidth: .infinity)
            }.padding(12)
            ScrollView {
                VStack(alignment: .leading, spacing: 8) { content() }.padding(.horizontal, 16).padding(.bottom, 24)
            }
        }
    }
}

struct Btn: View {
    let label: String
    let color: Color
    var size: CGFloat = 20
    var full = false
    let action: () -> Void

    init(_ label: String, _ color: Color, size: CGFloat = 20, full: Bool = false, action: @escaping () -> Void) {
        self.label = label; self.color = color; self.size = size; self.full = full; self.action = action
    }

    var body: some View {
        Button(action: { Sound.shared.tap(); action() }) {
            Text(label).font(.system(size: size, weight: .bold)).foregroundColor(.white)
                .padding(.horizontal, 16).padding(.vertical, 9)
                .frame(maxWidth: full ? .infinity : nil)
                .background(RoundedRectangle(cornerRadius: 18).fill(color))
        }.buttonStyle(.plain)
    }
}

/** احتفال: نجوم تطير والشخصية تكبر في النص */
struct CelebrationView: View {
    let kid: Kid
    @EnvironmentObject var nav: Nav
    @State private var go = false

    var body: some View {
        GeometryReader { g in
            ZStack {
                Color.black.opacity(0.15).ignoresSafeArea()
                ForEach(0..<18, id: \.self) { i in
                    Text(["⭐", "🌟", "🎉", "✨", "💛"][i % 5]).font(.system(size: 40))
                        .position(x: g.size.width * CGFloat((i * 37) % 100) / 100, y: go ? g.size.height * 0.2 - CGFloat(i % 5) * 40 : g.size.height + 40)
                        .rotationEffect(.degrees(go ? 360 : 0))
                        .opacity(go ? 0.3 : 1)
                        .animation(.easeOut(duration: 1.1).delay(Double(i) * 0.03), value: go)
                }
                CharacterFigure(kid: kid).frame(width: 190, height: 300)
                    .scaleEffect(go ? 1 : 0.2)
                    .animation(.spring(response: 0.5), value: go)
            }
        }
        .allowsHitTesting(false)
        .onAppear { go = true }
    }
}
