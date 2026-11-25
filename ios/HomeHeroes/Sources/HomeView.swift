import SwiftUI

func charHeight(_ id: String) -> CGFloat {
    switch id { case "ayan": return 95; case "layan": return 120; case "rayan": return 135; default: return 150 }
}

/** يدير فقاعات الكلام بين الأطفال الموجودين في نفس الغرفة */
final class Chatter: ObservableObject {
    @Published var lines: [String: String] = [:]
    private var timer: Timer?

    func say(_ kid: String, _ s: String, _ secs: Double = 2.6) {
        lines[kid] = s
        DispatchQueue.main.asyncAfter(deadline: .now() + secs) { [weak self] in
            if self?.lines[kid] == s { self?.lines[kid] = nil }
        }
    }

    func start(visible: @escaping () -> [Kid], roomOf: @escaping (String) -> String) {
        stop()
        timer = Timer.scheduledTimer(withTimeInterval: 7, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let vis = visible()
            let ids = Set(vis.map { $0.id })
            let together = Talk.convos.filter { ids.contains($0.a) && ids.contains($0.b) && roomOf($0.a) == roomOf($0.b) }
            if let c = together.randomElement(), Double.random(in: 0...1) < 0.75 {
                self.say(c.a, c.lineA)
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) { self.say(c.b, c.lineB) }
            } else if let k = vis.randomElement() {
                self.say(k.id, Talk.solo(k, roomOf(k.id)).randomElement() ?? "")
            }
        }
    }

    func stop() { timer?.invalidate(); timer = nil }
}

struct Bubble: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 16, weight: .bold)).foregroundColor(Color(hex: 0x212529))
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 16).fill(.white))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(hex: 0x343A40), lineWidth: 2))
            .fixedSize()
    }
}

struct HomeView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @StateObject private var chat = Chatter()
    @State private var selected: String? = nil
    @State private var walkingKid: String? = nil
    @Namespace private var ns

    var body: some View {
        VStack(spacing: 6) {
            topBar
            VStack(spacing: 8) {
                ForEach([0, 2], id: \.self) { i in
                    HStack(spacing: 8) {
                        roomCard(Rooms.all[i])
                        roomCard(Rooms.all[i + 1])
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, 8)
            familyBar
        }
        .onAppear {
            if store.checkFamilyBonus() { nav.flash("🎉 العيلة كلها فازت بتحدي اليوم! +10 لكل واحد") }
        }
        .onDisappear { chat.stop() }
    }

    private var topBar: some View {
        HStack {
            Text("🏠 أبطال البيت").font(.system(size: 24, weight: .bold)).foregroundColor(orange)
            Spacer()
            ForEach(Family.kids) { kid in
                Button { nav.show(.kid(kid.id)) } label: {
                    HStack(spacing: 4) {
                        Avatar(kid: kid, size: 40)
                        Text("⭐\(store.points(kid.id))").font(.system(size: 17, weight: .bold)).foregroundColor(kid.color)
                    }
                    .padding(.vertical, 2).padding(.leading, 12).padding(.trailing, 4)
                    .background(Capsule().fill(.white)).overlay(Capsule().stroke(kid.color, lineWidth: 3))
                }.buttonStyle(.plain)
            }
            Spacer()
            let n = store.requests().count
            Btn(n > 0 ? "🔒 \(n)" : "🔒", Color(hex: 0x5C5F66), size: 18) { ParentGate.open(store, nav) }
        }.padding(.horizontal, 12).padding(.top, 8)
    }

    private func todo(_ room: String) -> Int {
        let ks = selected.map { [$0] } ?? Family.kids.map { $0.id }
        return ks.reduce(0) { acc, k in acc + store.chores(k).filter { $0.room == room && store.taskState(k, $0.id) == .todo }.count }
    }

    private func roomCard(_ room: Room) -> some View {
        ZStack(alignment: .topTrailing) {
            // الخلفية لا تتحكم في حجم البطاقة: حجم البطاقة يحدده التخطيط، والصورة تملأها وتُقص
            Color.clear
                .overlay(Image(uiImage: image(room.bg)).resizable().scaledToFill())
                .clipped()
            let n = todo(room.id)
            Text("\(room.emoji) \(room.name)" + (n > 0 ? "  ⭐\(n)" : ""))
                .font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                .padding(.horizontal, 10).padding(.vertical, 4)
                .background(Capsule().fill(Color(hex: 0x5C3D2E).opacity(0.8))).padding(8)
            VStack {
                Spacer()
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(Family.kids.filter { store.kidRoom($0.id) == room.id }) { kid in
                        figure(kid)
                    }
                }.padding(.bottom, 6)
            }.frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: 0x8D5524), lineWidth: 4))
        .contentShape(Rectangle())
        .onTapGesture {
            if let sel = selected, store.kidRoom(sel) != room.id {
                let kid = Family.byId(sel)
                Speaker.shared.say(kid.toddler ? "يلا!" : "رايح \(room.name)", as: kid.id)
                walkingKid = sel
                withAnimation(.easeInOut(duration: 1.2)) { store.setKidRoom(sel, room.id) }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.25) { walkingKid = nil }
                selected = nil
            } else {
                selected = nil
                nav.show(.room(room.id))
            }
        }
    }

    private func figure(_ kid: Kid) -> some View {
        let h = charHeight(kid.id) * 0.8
        return CharacterFigure(kid: kid, walking: walkingKid == kid.id)
            .frame(width: h * 0.62, height: h)
            .scaleEffect(selected == kid.id ? 1.15 : 1, anchor: .bottom)
            .overlay(alignment: .top) {
                if let l = chat.lines[kid.id] { Bubble(text: l).offset(y: -34) }
            }
            .matchedGeometryEffect(id: kid.id, in: ns)
            .zIndex(walkingKid == kid.id ? 10 : 1)
            .onTapGesture {
                if selected == kid.id {
                    selected = nil
                    nav.show(.room(store.kidRoom(kid.id)))
                } else {
                    withAnimation(.easeOut(duration: 0.15)) { selected = kid.id }
                    Speaker.shared.say(kid.name)
                    chat.say(kid.id, kid.toddler ? "وين أروح؟ 🙂" : "وين أروح؟ اختار غرفة!")
                }
            }
            .onLongPressGesture { nav.show(.kid(kid.id)) }
    }

    private var familyBar: some View {
        let done = store.familyDone(), goal = store.familyGoal()
        return HStack {
            Text("🤝 تحدي العيلة").font(.system(size: 17, weight: .bold)).foregroundColor(orange)
            ProgressView(value: Double(min(done, goal)), total: Double(goal)).tint(orange)
            Text(store.bonusGivenToday ? "🎉 فزتوا!" : "\(done) / \(goal)").font(.system(size: 17, weight: .bold))
        }
        .padding(.horizontal, 14).padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color(hex: 0xFFE8CC)))
        .padding(.horizontal, 8).padding(.bottom, 6)
    }
}
