import SwiftUI

/** غرض في الغرفة يمكن سحبه */
struct Thing: Identifiable {
    let id: Int
    let emoji: String
    var pos: CGPoint
    let task: DragTask?
    var hidden = false
    var busy = false
}

func finishChore(_ store: Store, _ nav: Nav, _ kid: Kid, _ ch: Chore) {
    guard store.taskState(kid.id, ch.id) == .todo else { return }
    store.setTaskState(kid.id, ch.id, .pending)
    store.addRequest(Request(id: store.newRequestId(), kidId: kid.id, kind: "chore", refId: ch.id, title: ch.title, points: ch.points))
    Speaker.shared.say(kid.toddler ? "شاطر يا \(kid.name)!" : "أحسنت يا \(kid.name)! بانتظار اعتماد الأهل")
    nav.celebrating = kid
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.7) { nav.celebrating = nil }
}

struct RoomView: View {
    let room: Room
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav

    var body: some View {
        let here = Family.kids.filter { store.kidRoom($0.id) == room.id }
        let tasks = here.flatMap { k in store.chores(k.id).filter { $0.room == room.id }.map { (k, $0) } }
        let drags = tasks.filter { store.taskState($0.0.id, $0.1.id) == .todo }.compactMap { RoomItems.dragFor($0.0, $0.1, room.id) }
        ZStack {
            Image(uiImage: image(room.bg)).resizable().scaledToFill().ignoresSafeArea()
                .frame(maxWidth: .infinity, maxHeight: .infinity).clipped()
            VStack(spacing: 4) {
                HStack {
                    Btn("🏠 البيت", orange, size: 18) { nav.show(.home) }
                    Text("  \(room.emoji) \(room.name)").font(.system(size: 26, weight: .bold)).foregroundColor(.white)
                        .shadow(color: .black, radius: 3)
                    Spacer()
                    ForEach(Family.kids.filter { store.kidRoom($0.id) != room.id }) { kid in
                        Button {
                            Speaker.shared.say("جاي!")
                            store.setKidRoom(kid.id, room.id)
                        } label: {
                            HStack(spacing: 4) {
                                Avatar(kid: kid, size: 38)
                                Text("تعال").font(.system(size: 15, weight: .bold)).foregroundColor(kid.color)
                            }
                            .padding(.vertical, 2).padding(.leading, 10).padding(.trailing, 4)
                            .background(Capsule().fill(Color.white.opacity(0.93))).overlay(Capsule().stroke(kid.color, lineWidth: 2))
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 12).padding(.top, 8)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        if here.isEmpty { Note(text: "ما فيه أحد هنا! اضغط «تعال» عشان تنادي أحد 👆") }
                        else if tasks.isEmpty { Note(text: "ما فيه مهام هنا. جرّبوا غرفة ثانية! 🚪") }
                        else { ForEach(0..<tasks.count, id: \.self) { i in TaskTile(room: room, kid: tasks[i].0, chore: tasks[i].1) } }
                    }.padding(.horizontal, 8)
                }.frame(height: 150)

                SceneArea(room: room, kids: here, tasks: drags)
                    .padding(.bottom, 6)
            }
        }
    }
}

struct Note: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 20, weight: .bold))
            .padding(.horizontal, 16).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white.opacity(0.93)))
    }
}

struct TaskTile: View {
    let room: Room
    let kid: Kid
    let chore: Chore
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav

    var body: some View {
        let st = store.taskState(kid.id, chore.id)
        let fillColor: Color = st == .todo ? .white : (st == .pending ? Color(hex: 0xFFF3BF) : Color(hex: 0xD3F9D8))
        let dt = RoomItems.dragFor(kid, chore, room.id)
        VStack(spacing: 4) {
            HStack { Avatar(kid: kid, size: 34); Text(chore.emoji).font(.system(size: kid.toddler ? 40 : 32)) }
            Text(chore.title).font(.system(size: 15, weight: .bold)).multilineTextAlignment(.center).lineLimit(2)
            if !kid.toddler { Text("⭐ \(chore.points)").font(.system(size: 13, weight: .bold)).foregroundColor(kid.color) }
            switch st {
            case .pending: Text("⏳").font(.system(size: 24))
            case .done: Text("✅").font(.system(size: 24))
            case .todo:
                if let d = dt {
                    Text("\(d.obj) ➜ \(d.target.emoji)").font(.system(size: 22))
                } else {
                    Btn(kid.toddler ? "👍" : "خلّصت!", green, size: kid.toddler ? 24 : 15) { finishChore(store, nav, kid, chore) }
                }
            }
        }
        .padding(8)
        .frame(width: kid.toddler ? 180 : 165)
        .background(RoundedRectangle(cornerRadius: 22).fill(fillColor.opacity(0.95)))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(kid.color, lineWidth: 3))
        .onTapGesture {
            if let d = dt, st == .todo { Speaker.shared.say("\(chore.title). اسحبها إلى \(d.target.name)") }
            else { Speaker.shared.say(chore.title) }
        }
    }
}

/** أرضية الغرفة: أماكن ثابتة، أغراض تنسحب، وشخصيات تمشي وتحمل */
struct SceneArea: View {
    let room: Room
    let kids: [Kid]
    let tasks: [DragTask]
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @StateObject private var chat = Chatter()

    @State private var size: CGSize = .zero
    @State private var things: [Thing] = []
    @State private var feet: [String: CGPoint] = [:]
    @State private var walking: Set<String> = []
    @State private var carrying: [String: String] = [:]
    @State private var bend: [String: CGFloat] = [:]
    @State private var busy: Set<String> = []
    @State private var selected: String? = nil
    @State private var ghost: (Int, CGPoint)? = nil

    private func charH(_ id: String) -> CGFloat {
        size.height * (id == "ayan" ? 0.42 : id == "layan" ? 0.52 : id == "rayan" ? 0.58 : 0.64)
    }

    private func targetRect(_ t: Target) -> CGRect {
        CGRect(x: t.fx * size.width - 55, y: t.fy * size.height - 45, width: 110, height: 90)
    }

    var body: some View {
        GeometryReader { g in
            ZStack {
                ForEach(RoomItems.targets(room.id)) { t in
                    VStack(spacing: 0) {
                        Text(t.emoji).font(.system(size: 42))
                        Text(t.name).font(.system(size: 13, weight: .bold)).foregroundColor(Color(hex: 0x5C3D2E))
                    }
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(RoundedRectangle(cornerRadius: 18).fill(Color.white.opacity(0.8)))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: 0x8D5524), lineWidth: 2))
                    .position(x: t.fx * g.size.width, y: t.fy * g.size.height)
                    .onTapGesture { Speaker.shared.say(t.name) }
                }
                ForEach(things) { th in
                    if !th.hidden { thingView(th) }
                }
                ForEach(kids) { kid in
                    let h = charH(kid.id)
                    let f = feet[kid.id] ?? CGPoint(x: g.size.width / 2, y: g.size.height)
                    CharacterFigure(kid: kid, walking: walking.contains(kid.id), carrying: carrying[kid.id], bend: bend[kid.id] ?? 0)
                        .frame(width: h * 0.62, height: h)
                        .overlay(alignment: .top) {
                            if let l = chat.lines[kid.id] { Bubble(text: l).offset(y: -34) }
                        }
                        .scaleEffect(selected == kid.id ? 1.08 : 1, anchor: .bottom)
                        .position(x: f.x, y: f.y - h / 2)
                        .zIndex(walking.contains(kid.id) ? 5 : 2)
                        .onTapGesture {
                            selected = kid.id
                            chat.say(kid.id, Talk.solo(kid, room.id).randomElement() ?? "")
                        }
                }
                if let gh = ghost, let th = things.first(where: { $0.id == gh.0 }) {
                    Text(th.emoji).font(.system(size: 58)).opacity(0.75).position(gh.1).allowsHitTesting(false).zIndex(20)
                }
            }
            .coordinateSpace(name: "scene")
            .onAppear { setup(g.size) }
            .onChange(of: g.size) { s in if size == .zero { setup(s) } }
        }
        .onDisappear { chat.stop() }
    }

    private func thingView(_ th: Thing) -> some View {
        Text(th.emoji).font(.system(size: th.task != nil ? 50 : 40))
            .padding(4)
            .background(Circle().fill(Color.white.opacity(th.task != nil ? 0.35 : 0)))
            .overlay(Circle().stroke(th.task?.kid.color ?? .clear, lineWidth: 3))
            .position(th.pos)
            .zIndex(3)
            .gesture(
                DragGesture(minimumDistance: 4, coordinateSpace: .named("scene"))
                    .onChanged { v in
                        if th.busy { return }
                        if ghost == nil, let t = th.task { Speaker.shared.say(t.kid.toddler ? t.target.name : "ودّها \(t.target.name)") }
                        ghost = (th.id, v.location)
                    }
                    .onEnded { v in
                        ghost = nil
                        if th.busy { return }
                        drop(th.id, at: v.location)
                    }
            )
    }

    private func setup(_ s: CGSize) {
        guard s.width > 0, size == .zero else { return }
        size = s
        var list: [Thing] = []
        var i = 0
        for t in tasks {
            let sp = RoomItems.spots[i % RoomItems.spots.count]
            list.append(Thing(id: i, emoji: t.obj, pos: CGPoint(x: sp.0 * s.width, y: sp.1 * s.height), task: t)); i += 1
        }
        for d in RoomItems.decor(room.id) {
            let sp = RoomItems.spots[i % RoomItems.spots.count]
            list.append(Thing(id: i, emoji: d, pos: CGPoint(x: sp.0 * s.width, y: sp.1 * s.height), task: nil)); i += 1
        }
        things = list
        for (idx, k) in kids.enumerated() {
            feet[k.id] = CGPoint(x: s.width * CGFloat(idx + 1) / CGFloat(kids.count + 1), y: s.height)
        }
        selected = kids.first?.id
        chat.start(visible: { kids }, roomOf: { _ in room.id })
    }

    private func clampFoot(_ id: String, _ p: CGPoint) -> CGPoint {
        let h = charH(id)
        let w = h * 0.62
        return CGPoint(x: min(max(p.x, w / 2), size.width - w / 2), y: min(max(p.y, size.height * 0.25 + h * 0.8), size.height))
    }

    private func walk(_ id: String, to p: CGPoint) async {
        let target = clampFoot(id, p)
        let from = feet[id] ?? target
        let dist = hypot(target.x - from.x, target.y - from.y)
        if dist < 4 { return }
        let dur = min(max(Double(dist / (size.width * 0.35)), 0.35), 2.6)
        walking.insert(id)
        withAnimation(.linear(duration: dur)) { feet[id] = target }
        try? await Task.sleep(nanoseconds: UInt64(dur * 1_000_000_000))
        walking.remove(id)
    }

    private func pick(_ id: String) async {
        withAnimation(.easeInOut(duration: 0.22)) { bend[id] = 1 }
        try? await Task.sleep(nanoseconds: 230_000_000)
        withAnimation(.easeInOut(duration: 0.22)) { bend[id] = 0 }
        try? await Task.sleep(nanoseconds: 230_000_000)
    }

    private func drop(_ thingId: Int, at p: CGPoint) {
        guard let idx = things.firstIndex(where: { $0.id == thingId }) else { return }
        let th = things[idx]
        if hypot(p.x - th.pos.x, p.y - th.pos.y) < 30 { return }
        let hit = RoomItems.targets(room.id).first { targetRect($0).insetBy(dx: -24, dy: -24).contains(p) }
        guard let kidId = th.task?.kid.id ?? selected ?? kids.first?.id else {
            Speaker.shared.say("نادِ أحد أول")
            return
        }
        if busy.contains(kidId) {
            chat.say(kidId, "لحظة! 🙂")
            return
        }
        let correct = th.task != nil && hit?.id == th.task?.target.id
        let dest: CGPoint = hit.map { CGPoint(x: $0.fx * size.width, y: $0.fy * size.height + 50) } ?? p
        busy.insert(kidId)
        things[idx].busy = true
        Task { @MainActor in
            await walk(kidId, to: CGPoint(x: th.pos.x, y: th.pos.y + 30))
            await pick(kidId)
            carrying[kidId] = th.emoji
            if let i = things.firstIndex(where: { $0.id == thingId }) { things[i].hidden = true }
            await walk(kidId, to: dest)
            await pick(kidId)
            carrying[kidId] = nil
            busy.remove(kidId)
            guard let i = things.firstIndex(where: { $0.id == thingId }) else { return }
            if let t = hit {
                if th.task == nil || correct {
                    things.remove(at: i)
                } else {
                    things[i].pos = CGPoint(x: t.fx * size.width, y: min(t.fy * size.height + 90, size.height - 30))
                    things[i].hidden = false
                    things[i].busy = false
                }
            } else {
                things[i].pos = p
                things[i].hidden = false
                things[i].busy = false
            }
            if correct, let t = th.task {
                finishChore(store, nav, t.kid, t.chore)
            } else if let t = th.task {
                let msg = "مو هنا! مكانه \(t.target.emoji) \(t.target.name)"
                chat.say(kidId, msg)
                Speaker.shared.say(t.kid.toddler ? t.target.name : msg)
            }
        }
    }
}
