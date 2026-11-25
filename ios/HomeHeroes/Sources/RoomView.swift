import SwiftUI

func finishChore(_ store: Store, _ nav: Nav, _ kid: Kid, _ ch: Chore) {
    guard store.taskState(kid.id, ch.id) == .todo else { return }
    store.setTaskState(kid.id, ch.id, .pending)
    Sound.shared.success()
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
        GeometryReader { _ in
            ZStack(alignment: .top) {
                // المشهد (الخلفية والأثاث والشخصيات) داخل المنطقة الآمنة، حتى تتطابق الأماكن مع الرسم
                SceneArea(room: room, kids: here, tasks: drags)
                VStack(spacing: 4) {
                    HStack {
                        Btn("🏠 البيت", orange, size: 18) { nav.show(.home) }
                        Text("  \(room.emoji) \(room.name)").font(.system(size: 24, weight: .bold)).foregroundColor(.white)
                            .shadow(color: .black, radius: 3)
                        Spacer()
                        ForEach(Family.kids.filter { store.kidRoom($0.id) != room.id }) { kid in
                            Button {
                                Sound.shared.tap()
                                Speaker.shared.say("جاي!", as: kid.id)
                                store.setKidRoom(kid.id, room.id)
                            } label: {
                                HStack(spacing: 4) {
                                    Avatar(kid: kid, size: 34)
                                    Text("تعال").font(.system(size: 14, weight: .bold)).foregroundColor(kid.color)
                                }
                                .padding(.vertical, 2).padding(.leading, 10).padding(.trailing, 4)
                                .background(Capsule().fill(Color.white.opacity(0.93))).overlay(Capsule().stroke(kid.color, lineWidth: 2))
                            }.buttonStyle(.plain)
                        }
                    }.padding(.horizontal, 8).padding(.top, 4)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            if here.isEmpty { Note(text: "ما فيه أحد هنا! اضغط «تعال» عشان تنادي أحد 👆") }
                            else if tasks.isEmpty { Note(text: "ما فيه مهام هنا. جرّبوا غرفة ثانية! 🚪") }
                            else { ForEach(0..<tasks.count, id: \.self) { i in TaskTile(room: room, kid: tasks[i].0, chore: tasks[i].1) } }
                        }.padding(.horizontal, 8)
                    }.frame(height: 56)
                    Spacer(minLength: 0).allowsHitTesting(false)
                }
            }
        }
        .background(RoomItems.art(room.id).wall.ignoresSafeArea())
    }
}

struct Note: View {
    let text: String
    var body: some View {
        Text(text).font(.system(size: 17, weight: .bold))
            .padding(.horizontal, 14).padding(.vertical, 8)
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
        // بطاقة مختصرة بسطر واحد حتى لا تغطي أثاث الغرفة
        HStack(spacing: 6) {
            Avatar(kid: kid, size: 34)
            Text(chore.emoji).font(.system(size: kid.toddler ? 28 : 22))
            VStack(alignment: .leading, spacing: 0) {
                Text(chore.title).font(.system(size: 14, weight: .bold)).lineLimit(1)
                if !kid.toddler { Text("⭐ \(chore.points)").font(.system(size: 12, weight: .bold)).foregroundColor(kid.color) }
            }
            switch st {
            case .pending: Text("⏳").font(.system(size: 20))
            case .done: Text("✅").font(.system(size: 20))
            case .todo:
                if let d = dt {
                    Text("\(d.obj)➜\(d.target.emoji)").font(.system(size: 20))
                } else {
                    Btn(kid.toddler ? "👍" : "خلّصت!", green, size: kid.toddler ? 20 : 14) { finishChore(store, nav, kid, chore) }
                }
            }
        }
        .padding(.vertical, 3).padding(.leading, 4).padding(.trailing, 10)
        .background(Capsule().fill(fillColor.opacity(0.95)))
        .overlay(Capsule().stroke(kid.color, lineWidth: 3))
        .onTapGesture {
            if let d = dt, st == .todo { Speaker.shared.say("\(chore.title). اسحبها إلى \(d.target.name)") }
            else { Speaker.shared.say(chore.title) }
        }
    }
}

/** غرض في الغرفة يمكن سحبه (الموضع = مركز الغرض بإحداثيات المشهد) */
struct Thing: Identifiable {
    let id: Int
    let emoji: String
    var pos: CGPoint
    let task: DragTask?
    var hidden = false
    var busy = false
    var fading = false
}

/**
 * المشهد: الخلفية مرسومة بنفس مقياس الإحداثيات (عرض كامل، والصورة ملتصقة بالأسفل)،
 * والأغراض تنسحب، والشخصيات تمشي على الأرضية فقط.
 * الاتجاه من اليسار لليمين داخل المشهد حتى لا تنعكس حركة السحب في واجهة عربية.
 */
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
    @State private var facingLeft: Set<String> = []
    @State private var carrying: [String: String] = [:]
    @State private var bend: [String: CGFloat] = [:]
    @State private var busy: Set<String> = []
    @State private var selected: String? = nil
    @State private var ghost: (Int, CGPoint)? = nil
    @State private var pulse: String? = nil

    private var art: RoomArt { RoomItems.art(room.id) }
    private var scale: CGFloat { size.width / art.iw }
    private var offY: CGFloat { size.height - art.ih * scale }
    private func sx(_ fx: CGFloat) -> CGFloat { fx * art.iw * scale }
    private func sy(_ fy: CGFloat) -> CGFloat { offY + fy * art.ih * scale }
    private var floorY: CGFloat { min(max(sy(art.floorTop), size.height * 0.35), size.height * 0.88) }

    private func charH(_ id: String) -> CGFloat {
        size.height * (id == "ayan" ? 0.30 : id == "layan" ? 0.36 : id == "rayan" ? 0.41 : 0.45)
    }

    private func clampFloorY(_ y: CGFloat) -> CGFloat { min(max(y, floorY + 4), size.height - 2) }

    /** مكان العلامة/الغرض الذي يمثل المكان على الشاشة */
    private func targetPoint(_ t: Target) -> CGPoint {
        CGPoint(x: min(max(sx(t.fx), 40), size.width - 40), y: t.prop ? sy(t.fy) - 28 : sy(t.fy) - 10)
    }

    /** مكان وقوف الشخصية أمام المكان (على الأرض دائمًا) */
    private func standFor(_ t: Target) -> CGPoint {
        let x = sx(t.fx)
        if t.prop {
            let side: CGFloat = x > size.width / 2 ? -1 : 1
            return CGPoint(x: min(max(x + side * 70, 40), size.width - 40), y: clampFloorY(sy(t.fy) + 10))
        }
        return CGPoint(x: min(max(x, 40), size.width - 40), y: clampFloorY(floorY + (size.height - floorY) * 0.3))
    }

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .topLeading) {
                if size != .zero {
                    let imgH = art.ih * scale
                    Image(uiImage: image(room.bg)).resizable()
                        .frame(width: size.width, height: imgH)
                        .position(x: size.width / 2, y: offY + imgH / 2)
                    ForEach(art.targets) { t in targetView(t).position(targetPoint(t)) }
                    ForEach(things) { th in
                        if !th.hidden { thingView(th) }
                    }
                    ForEach(kids) { kid in
                        let h = charH(kid.id)
                        let f = feet[kid.id] ?? CGPoint(x: size.width / 2, y: size.height - 4)
                        CharacterFigure(kid: kid, walking: walking.contains(kid.id), carrying: carrying[kid.id], bend: bend[kid.id] ?? 0)
                            .frame(width: h * 0.45, height: h)
                            .scaleEffect(x: facingLeft.contains(kid.id) ? -1 : 1, y: 1)
                            .overlay(alignment: .top) {
                                if let l = chat.lines[kid.id] {
                                    Bubble(text: l).environment(\.layoutDirection, .rightToLeft).offset(y: -34)
                                }
                            }
                            .scaleEffect(selected == kid.id ? 1.08 : 1, anchor: .bottom)
                            .position(x: f.x, y: f.y - h / 2)
                            .zIndex(Double(f.y) + (walking.contains(kid.id) ? 1000 : 0))
                            .onTapGesture {
                                Sound.shared.tap()
                                selected = kid.id
                                let line = Talk.solo(kid, room.id).randomElement() ?? ""
                                chat.say(kid.id, line)
                                Speaker.shared.say(line, as: kid.id)
                            }
                    }
                    if let gh = ghost, let th = things.first(where: { $0.id == gh.0 }) {
                        Text(th.emoji).font(.system(size: 52)).opacity(0.8).position(gh.1).allowsHitTesting(false).zIndex(5000)
                    }
                }
            }
            .frame(width: g.size.width, height: g.size.height)
            .clipped()
            .contentShape(Rectangle())
            .coordinateSpace(name: "scene")
            .onAppear { setup(g.size) }
            .onChange(of: g.size) { s in if size == .zero { setup(s) } }
        }
        .environment(\.layoutDirection, .leftToRight)
        .onDisappear { chat.stop() }
    }

    @ViewBuilder private func targetView(_ t: Target) -> some View {
        VStack(spacing: 2) {
            if t.prop {
                Text(t.emoji).font(.system(size: 42))
            } else {
                Circle().fill(Color.white.opacity(0.2))
                    .overlay(Circle().stroke(Color.white.opacity(0.85), lineWidth: 3))
                    .frame(width: 50, height: 50)
            }
            Text(t.name).font(.system(size: 12, weight: .bold)).foregroundColor(Color(hex: 0x5C3D2E))
                .padding(.horizontal, 8).padding(.vertical, 1)
                .background(Capsule().fill(Color.white.opacity(0.9)))
        }
        .scaleEffect(pulse == t.id ? 1.18 : 1)
        .zIndex(1)
        .onTapGesture { Sound.shared.tap(); Speaker.shared.say(t.name) }
    }

    private func thingView(_ th: Thing) -> some View {
        Text(th.emoji).font(.system(size: th.task != nil ? 42 : 34))
            .padding(4)
            .background(Circle().fill(Color.white.opacity(th.task != nil ? 0.35 : 0)))
            .overlay(Circle().stroke(th.task?.kid.color ?? .clear, lineWidth: 3))
            .scaleEffect(th.fading ? 0.2 : 1)
            .opacity(th.fading ? 0 : 1)
            .position(th.pos)
            .zIndex(Double(th.pos.y) + (th.busy ? 2000 : 0))
            .gesture(
                DragGesture(minimumDistance: 4, coordinateSpace: .named("scene"))
                    .onChanged { v in
                        if th.busy { return }
                        if ghost == nil {
                            Sound.shared.tap()
                            if let t = th.task { Speaker.shared.say(t.kid.toddler ? t.target.name : "ودّها \(t.target.name)") }
                        }
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
        guard s.width > 0, s.height > 0, size == .zero else { return }
        size = s
        var list: [Thing] = []
        var i = 0
        func spot(_ i: Int) -> CGPoint {
            let sp = RoomItems.spots[i % RoomItems.spots.count]
            let x = min(max(sp.0 * s.width, 30), s.width - 30)
            return CGPoint(x: x, y: floorY + (s.height - floorY) * sp.1 - 24)
        }
        for t in tasks { list.append(Thing(id: i, emoji: t.obj, pos: spot(i), task: t)); i += 1 }
        for d in RoomItems.decor(room.id) { list.append(Thing(id: i, emoji: d, pos: spot(i), task: nil)); i += 1 }
        things = list
        for (idx, k) in kids.enumerated() {
            feet[k.id] = CGPoint(x: s.width * CGFloat(idx + 1) / CGFloat(kids.count + 1), y: s.height - 4)
        }
        selected = kids.first?.id
    }

    private func clampFoot(_ id: String, _ p: CGPoint) -> CGPoint {
        let w = charH(id) * 0.45
        return CGPoint(x: min(max(p.x, w / 2), size.width - w / 2), y: clampFloorY(p.y))
    }

    /** المشي على الأرضية فقط: القدمان لا تطلعان فوق خط الأرضية */
    private func walk(_ id: String, to p: CGPoint) async {
        let target = clampFoot(id, p)
        let from = feet[id] ?? target
        let dist = hypot(target.x - from.x, target.y - from.y)
        if dist < 4 { return }
        let dur = min(max(Double(dist / (size.width * 0.35)), 0.35), 2.6)
        if target.x < from.x { facingLeft.insert(id) } else { facingLeft.remove(id) }
        walking.insert(id)
        withAnimation(.linear(duration: dur)) { feet[id] = target }
        try? await Task.sleep(nanoseconds: UInt64(dur * 1_000_000_000))
        walking.remove(id)
        facingLeft.remove(id)
    }

    private func pick(_ id: String) async {
        withAnimation(.easeInOut(duration: 0.22)) { bend[id] = 1 }
        try? await Task.sleep(nanoseconds: 230_000_000)
        withAnimation(.easeInOut(duration: 0.22)) { bend[id] = 0 }
        try? await Task.sleep(nanoseconds: 230_000_000)
    }

    private func hitTarget(_ p: CGPoint) -> Target? {
        var best: Target? = nil
        var bestD = CGFloat.greatestFiniteMagnitude
        for t in art.targets {
            let c = targetPoint(t)
            let d = hypot(p.x - c.x, p.y - c.y)
            if d < 95, d < bestD { best = t; bestD = d }
        }
        return best
    }

    private func drop(_ thingId: Int, at p: CGPoint) {
        guard let idx = things.firstIndex(where: { $0.id == thingId }) else { return }
        let th = things[idx]
        if hypot(p.x - th.pos.x, p.y - th.pos.y) < 24 { return }
        let hit = hitTarget(p)
        guard let kidId = th.task?.kid.id ?? selected ?? kids.first?.id, kids.contains(where: { $0.id == kidId }) else {
            Speaker.shared.say("نادِ أحد أول")
            return
        }
        if busy.contains(kidId) {
            chat.say(kidId, "لحظة! 🙂")
            Speaker.shared.say("لحظة!", as: kidId)
            return
        }
        let correct = th.task != nil && hit?.id == th.task?.target.id
        let dest: CGPoint = hit.map { standFor($0) } ?? CGPoint(x: min(max(p.x, 30), size.width - 30), y: clampFloorY(p.y))
        busy.insert(kidId)
        things[idx].busy = true
        Task { @MainActor in
            // ١) يمشي للغرض على الأرض، ٢) ينحني ويمسكه، ٣) يمشي على الأرض إلى المكان
            await walk(kidId, to: CGPoint(x: th.pos.x, y: th.pos.y + 24))
            await pick(kidId)
            carrying[kidId] = th.emoji
            Sound.shared.pickUp()
            if let i = things.firstIndex(where: { $0.id == thingId }) { things[i].hidden = true }
            await walk(kidId, to: dest)
            await pick(kidId)
            carrying[kidId] = nil
            busy.remove(kidId)
            guard let i = things.firstIndex(where: { $0.id == thingId }) else { return }
            let f = feet[kidId] ?? dest
            let chest = CGPoint(x: f.x, y: f.y - charH(kidId) * 0.5)
            things[i].pos = chest
            things[i].hidden = false
            if let t = hit {
                // يحط الغرض في المكان
                withAnimation(.easeOut(duration: 0.32)) { things[i].pos = targetPoint(t) }
                try? await Task.sleep(nanoseconds: 330_000_000)
                guard let j = things.firstIndex(where: { $0.id == thingId }) else { return }
                if th.task == nil || correct {
                    withAnimation(.easeIn(duration: 0.25)) { things[j].fading = true; pulse = t.id }
                    try? await Task.sleep(nanoseconds: 260_000_000)
                    pulse = nil
                    things.removeAll { $0.id == thingId }
                } else {
                    // مكان غلط: يطيح على الأرض جنب الشخصية
                    withAnimation(.easeIn(duration: 0.35)) {
                        things[j].pos = CGPoint(x: min(f.x + 40, size.width - 30), y: clampFloorY(f.y) - 24)
                    }
                    things[j].busy = false
                }
            } else {
                withAnimation(.easeIn(duration: 0.3)) { things[i].pos = CGPoint(x: dest.x, y: dest.y - 24) }
                things[i].busy = false
            }
            if correct, let t = th.task {
                finishChore(store, nav, t.kid, t.chore)
            } else if let t = th.task {
                Sound.shared.wrong()
                let msg = "مو هنا! مكانه \(t.target.emoji) \(t.target.name)"
                chat.say(kidId, msg)
                Speaker.shared.say(t.kid.toddler ? t.target.name : msg, as: kidId)
            }
        }
    }
}
