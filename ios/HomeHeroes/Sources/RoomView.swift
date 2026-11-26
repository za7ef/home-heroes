import SwiftUI

struct RoomView: View {
    let room: Room
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav

    var body: some View {
        let here = Family.kids.filter { store.kidRoom($0.id) == room.id }
        GeometryReader { _ in
            ZStack(alignment: .top) {
                // المشهد (الخلفية والشخصيات وشريط الأدوات) داخل المنطقة الآمنة
                SceneArea(room: room, kids: here).id(here.map { $0.id }.joined())
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
                    if here.isEmpty { Note(text: "ما فيه أحد هنا! اضغط «تعال» عشان تنادي أحد 👆") }
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
        Text(text).font(.system(size: 17, weight: .bold)).foregroundColor(Color(hex: 0x212529))
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white.opacity(0.93)))
    }
}

/** حالة إمساك الأداة بالإصبع */
struct Grip {
    let tool: Tool
    var loc: CGPoint
    var last: CGPoint
    var lastT: Date
    var kid: String? = nil
    var progress: Double = 0
    var moved: CGFloat = 0
    var fxDist: CGFloat = 0
    var fxTime: Double = 0
    var lastDy: CGFloat = 0
    var steps = 0
    var done = false
    var warned = false
    var engaged = false
    var rot: Double = 0
    var flip: CGFloat = 1
    var scale: CGFloat = 1
    var temp: Double? = nil
}

/** جزيء صغير يطير ويختفي */
struct Part: Identifiable { let id: Int; let emoji: String; let p: CGPoint; let d: CGSize; let size: CGFloat; let born: Date; let dur: Double }

/** مشي من نقطة لنقطة (مكان القدمين) */
struct Walk { let from: CGPoint; let to: CGPoint; let start: Date; let dur: Double; let done: () -> Void }

/** أثر يطير فوق الرأس بعد الاستخدام */
struct Puff: Identifiable { let id: Int; let emoji: String; let p: CGPoint }

/**
 المشهد على طريقة ألعاب الحيوان الأليف: الشخصيات واقفة على الأرضية، وأدوات الغرفة في شريط تحت.
 تُسحب الأداة على الشخصية فتستخدمها ويرتفع مؤشر احتياجها، وفوق الرأس أيقونة الاحتياج الناقص.
 الاتجاه من اليسار لليمين داخل المشهد حتى لا تنعكس حركة السحب في واجهة عربية.
 */
struct SceneArea: View {
    let room: Room
    let kids: [Kid]
    @EnvironmentObject var store: Store
    @StateObject private var chat = Chatter()

    @State private var size: CGSize = .zero
    @State private var selected: String? = nil
    @State private var grip: Grip? = nil
    @State private var faces: [String: String] = [:]
    @State private var partial: [String: Double] = [:]
    @State private var parts: [Part] = []
    @State private var partSeq = 0
    @State private var door: [String: Double] = [:]
    @State private var wob = false
    @State private var bounceKid: String? = nil
    @State private var puffs: [Puff] = []
    @State private var puffUp = false
    @State private var now = Date()
    private let timer = Timer.publish(every: 4, on: .main, in: .common).autoconnect()
    // حركة الشخصيات
    @State private var pos: [String: CGPoint] = [:]
    @State private var poses: [String: String] = [:]
    @State private var facing: [String: CGFloat] = [:]
    @State private var props: [String: String] = [:]
    @State private var actFaces: [String: String] = [:]
    @State private var walks: [String: Walk] = [:]
    @State private var acting: Set<String> = []
    @State private var actGen: [String: Int] = [:]
    @State private var dragging: String? = nil
    @State private var dragFrom: CGPoint? = nil
    @State private var nextIdle = Date().addingTimeInterval(5)
    private let step = Timer.publish(every: 1.0 / 30, on: .main, in: .common).autoconnect()

    private let trayH: CGFloat = 76
    private var art: RoomArt { RoomItems.art(room.id) }
    private var scale: CGFloat { size.width / art.iw }
    private var offY: CGFloat { size.height - art.ih * scale }
    private var groundY: CGFloat { size.height - trayH - 4 }

    private func charH(_ id: String) -> CGFloat {
        size.height * (id == "ayan" ? 0.30 : id == "layan" ? 0.36 : id == "rayan" ? 0.41 : 0.45)
    }

    private func footX(_ idx: Int) -> CGFloat { size.width * CGFloat(idx + 1) / CGFloat(kids.count + 1) }

    /** مكان قدمي الطفل */
    private func feet(_ idx: Int) -> CGPoint { pos[kids[idx].id] ?? CGPoint(x: footX(idx), y: groundY) }

    private func frameOf(_ idx: Int) -> CGRect {
        let h = charH(kids[idx].id), w = h * 0.45
        let f = feet(idx)
        return CGRect(x: f.x - w / 2, y: f.y - h, width: w, height: h)
    }

    /** حدود الأرضية (مكان القدمين) */
    private var floorBand: (CGFloat, CGFloat) {
        let t = offY + art.floorTop * art.ih * scale
        return (t < groundY - 10 ? t + (groundY - t) * 0.1 : groundY, groundY)
    }

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .topLeading) {
                if size != .zero {
                    let imgH = art.ih * scale
                    Image(uiImage: image(room.bg)).resizable()
                        .frame(width: size.width, height: imgH)
                        .position(x: size.width / 2, y: offY + imgH / 2)
                    let _ = now
                    // الشخصيات: الأقرب للشاشة يرسم فوق
                    ZStack(alignment: .topLeading) {
                        ForEach(Array(kids.enumerated()), id: \.1.id) { idx, kid in
                            let r = frameOf(idx)
                            let pose = poses[kid.id] ?? "front"
                            CharacterFigure(kid: kid, walking: walks[kid.id] != nil, carrying: props[kid.id], bend: bounceKid == kid.id ? 0.5 : 0,
                                            face: faces[kid.id] ?? actFaces[kid.id], pose: pose, facing: facing[kid.id] ?? 1, held: dragging == kid.id)
                                .frame(width: pose == "crawl" ? r.height * 0.72 : r.width, height: r.height)
                                .overlay(alignment: .top) {
                                    if let l = chat.lines[kid.id] {
                                        Bubble(text: l).environment(\.layoutDirection, .rightToLeft).offset(y: pose == "crawl" ? r.height * 0.5 - 34 : -34)
                                    }
                                }
                                .scaleEffect(selected == kid.id ? 1.06 : 1, anchor: .bottom)
                                .contentShape(Rectangle())
                                .gesture(kidDrag(kid))
                                .position(x: r.midX, y: r.midY)
                                .zIndex(Double(r.maxY))
                        }
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    ForEach(Array(kids.enumerated()), id: \.1.id) { idx, kid in
                        let r = frameOf(idx)
                        if let m = Needs.lowest(store, kid.id), chat.lines[kid.id] == nil {
                            Text(m.emoji).font(.system(size: 26))
                                .frame(width: 48, height: 48)
                                .background(Circle().fill(.white)).overlay(Circle().stroke(red, lineWidth: 3))
                                .rotationEffect(.degrees(wob ? 10 : -10))
                                .position(x: r.midX, y: max(r.minY - 30, 70))
                                .onTapGesture { tapKid(kid) }
                        }
                        if let d = door[kid.id] {
                            Text("🚪").font(.system(size: r.height * 0.7)).opacity(d)
                                .position(x: r.midX, y: r.midY).allowsHitTesting(false)
                        }
                        if let gr = grip, gr.kid == kid.id, !gr.done, gr.progress > 0 {
                            ZStack(alignment: .leading) {
                                Capsule().fill(.white).frame(width: 84, height: 14).overlay(Capsule().stroke(green, lineWidth: 2))
                                Capsule().fill(Color(hex: 0x51CF66)).frame(width: max(4, 80 * min(1, gr.progress)), height: 10).padding(.leading, 2)
                            }
                            .position(x: r.midX, y: max(r.minY - 12, 10)).allowsHitTesting(false)
                        }
                    }
                    ForEach(puffs) { p in
                        Text(p.emoji).font(.system(size: 26))
                            .position(x: p.p.x, y: p.p.y - (puffUp ? 70 : 0))
                            .opacity(puffUp ? 0 : 1)
                            .allowsHitTesting(false)
                    }
                    tray.position(x: size.width / 2, y: size.height - trayH / 2 - 2)
                    TimelineView(.animation) { tl in
                        ZStack(alignment: .topLeading) {
                            ForEach(parts) { pt in
                                let f = min(1, tl.date.timeIntervalSince(pt.born) / pt.dur)
                                Text(pt.emoji).font(.system(size: pt.size))
                                    .position(x: pt.p.x + pt.d.width * f, y: pt.p.y + pt.d.height * f)
                                    .opacity(1 - f)
                            }
                        }
                        .frame(width: size.width, height: size.height, alignment: .topLeading)
                    }
                    .allowsHitTesting(false)
                    if let gr = grip, !gr.done {
                        let tp = tip(gr)
                        Text(gr.tool.emoji).font(.system(size: 56))
                            .scaleEffect(x: gr.flip * gr.scale, y: gr.scale)
                            .rotationEffect(.degrees(gr.rot))
                            .position(tp)
                            .allowsHitTesting(false)
                        if let t = gr.temp {
                            Text(String(format: "%.1f°", t)).font(.system(size: 18, weight: .bold)).foregroundColor(red)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(RoundedRectangle(cornerRadius: 10).fill(.white)).overlay(RoundedRectangle(cornerRadius: 10).stroke(red, lineWidth: 2))
                                .position(x: tp.x + 60, y: tp.y - 40).allowsHitTesting(false)
                        }
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
        .onReceive(timer) { now = $0 }
        .onReceive(step) { t in stepWalks(t) }
        .onDisappear { chat.stop() }
    }

    /** شريط الأدوات ومؤشرات الطفل المختار */
    private var tray: some View {
        HStack(spacing: 8) {
            if let k = kids.first(where: { $0.id == selected }) { meters(k) }
            ForEach(Needs.tools(room.id)) { t in
                Text(t.emoji).font(.system(size: 32))
                    .frame(width: 58, height: 58)
                    .background(Circle().fill(Color(hex: 0xFFF4D6))).overlay(Circle().stroke(Color(hex: 0xFFC078), lineWidth: 2))
                    .gesture(
                        DragGesture(minimumDistance: 0, coordinateSpace: .named("scene"))
                            .onChanged { v in
                                if grip == nil {
                                    Sound.shared.pickUp()
                                    grip = Grip(tool: t, loc: v.location, last: v.location, lastT: Date())
                                }
                                gripMove(v.location)
                            }
                            .onEnded { v in
                                guard let gr = grip else { return }
                                grip = nil
                                releaseGrip(gr)
                                if !gr.done {
                                    if gr.moved < 20 && v.time.timeIntervalSince(gr.lastT) < 0.35 && hypot(v.translation.width, v.translation.height) < 20 { Speaker.shared.say(t.name) }
                                    else if !gr.engaged { Speaker.shared.say("اسحبها على واحد من العيال") }
                                }
                            }
                    )
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 4)
        .frame(height: trayH)
        .background(RoundedRectangle(cornerRadius: 22).fill(Color.white.opacity(0.93)))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(orange, lineWidth: 3))
        .environment(\.colorScheme, .light)
    }

    private func meters(_ k: Kid) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(k.name).font(.system(size: 13, weight: .bold)).foregroundColor(k.color)
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { c in
                    VStack(alignment: .leading, spacing: 3) {
                        ForEach(Needs.meters[(c * 2)..<(c * 2 + 2)]) { m in
                            let v = store.need(k.id, m.id)
                            HStack(spacing: 4) {
                                Text(m.emoji).font(.system(size: 14))
                                ZStack(alignment: .leading) {
                                    Capsule().fill(Color(hex: 0xDEE2E6)).frame(width: 44, height: 9)
                                    Capsule().fill(v < Needs.low ? red : v < 80 ? Color(hex: 0xF59F00) : green)
                                        .frame(width: max(4, 44 * v / 100), height: 9)
                                }
                            }
                        }
                    }
                }
            }
        }
        .padding(.trailing, 6)
    }

    private func setup(_ s: CGSize) {
        guard s.width > 0, s.height > 0, size == .zero else { return }
        size = s
        selected = kids.first?.id
        withAnimation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true)) { wob = true }
        chat.start(store: store, visible: { kids.filter { !acting.contains($0.id) && dragging != $0.id } }, roomOf: { _ in room.id })
    }

    private func tapKid(_ kid: Kid) {
        cancelAct(kid.id)
        Sound.shared.tap()
        selected = kid.id
        bump(kid.id)
        let line = Needs.lowest(store, kid.id).map { Needs.needLine(store, kid, $0) } ?? Acts.soloLine(store, kid, room.id)
        chat.say(kid.id, line)
        Speaker.shared.say(line, as: kid.id)
    }

    private func bump(_ id: String) {
        withAnimation(.easeOut(duration: 0.12)) { bounceKid = id }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { withAnimation(.easeIn(duration: 0.15)) { if bounceKid == id { bounceKid = nil } } }
    }

    // MARK: - استخدام الأدوات بالإصبع

    /** الأداة تظهر فوق الإصبع قليلًا حتى يراها الطفل، ورأسها هو النقطة التي تلمس الشخصية */
    private func tip(_ gr: Grip) -> CGPoint { CGPoint(x: gr.loc.x, y: gr.loc.y - 58) }

    private func zoneHit(_ idx: Int, _ tool: Tool, _ p: CGPoint) -> Bool {
        let r = frameOf(idx), id = kids[idx].id
        switch tool.spot {
        case "mouth":
            let m = Face.mouth(id, in: r)
            return hypot(p.x - m.x, p.y - m.y) < max(r.width * 0.32, 38)
        case "head":
            let h = Face.head(id, in: r)
            return p.x > r.minX - 20 && p.x < r.maxX + 20 && p.y > h.y - 140 && p.y < h.y + r.height * 0.18
        default:
            return r.insetBy(dx: -16, dy: 0).contains(p)
        }
    }

    private func key(_ kid: String, _ t: Tool) -> String { kid + "|" + t.emoji }

    private func gripMove(_ loc: CGPoint) {
        guard var gr = grip else { return }
        let now = Date()
        let dx = loc.x - gr.last.x, dy = loc.y - gr.last.y
        let dist = hypot(dx, dy)
        let dt = min(0.12, max(0, now.timeIntervalSince(gr.lastT)))
        gr.last = loc; gr.lastT = now; gr.loc = loc
        gr.moved += dist
        defer { grip = gr }
        if gr.done { return }
        let tool = gr.tool
        let tp = tip(gr)
        let hitIdx = kids.indices.first { zoneHit($0, tool, tp) }
        let hit = hitIdx.map { kids[$0].id }

        if let prev = gr.kid, prev != hit { leave(&gr, prev) }
        guard let kidId = hit, let idx = hitIdx else {
            if tool.kind == "car" { if dx > 1 { gr.flip = -1 } else if dx < -1 { gr.flip = 1 } }
            else if tool.kind != "ball" { gr.rot = 0 }
            return
        }
        let kid = kids[idx]
        if gr.kid != kidId {
            gr.kid = kidId
            gr.engaged = true
            cancelAct(kidId)
            selected = kidId
            if store.need(kidId, tool.meter) >= 95 {
                if !gr.warned {
                    gr.warned = true
                    Sound.shared.wrong()
                    let l = Needs.full(kid)
                    chat.say(kidId, l); Speaker.shared.say(l, as: kidId)
                }
                return
            }
            gr.progress = partial[key(kidId, tool)] ?? 0
            faces[kidId] = Needs.use(tool.kind).face
        }
        if store.need(kidId, tool.meter) >= 95 { return }
        let use = Needs.use(tool.kind)
        gr.progress += Double(dist) / use.rub + dt / use.hold
        animate(&gr, idx, dx: dx, dy: dy, dist: dist, dt: dt)
        if gr.progress >= 1 { complete(&gr, kid) }
    }

    private func leave(_ gr: inout Grip, _ kid: String) {
        if !gr.done, gr.progress > 0, gr.progress < 1 { partial[key(kid, gr.tool)] = gr.progress }
        faces[kid] = nil
        gr.kid = nil
        gr.temp = nil
        gr.progress = 0
        if door[kid] != nil { withAnimation(.easeOut(duration: 0.2)) { door[kid] = nil } }
    }

    private func releaseGrip(_ gr: Grip) {
        var g = gr
        if let k = g.kid { leave(&g, k) }
    }

    private func complete(_ gr: inout Grip, _ kid: Kid) {
        gr.done = true
        partial[key(kid.id, gr.tool)] = nil
        faces[kid.id] = nil
        gr.temp = nil
        if door[kid.id] != nil { withAnimation(.easeOut(duration: 0.2)) { door[kid.id] = nil } }
        switch gr.tool.kind {
        case "toilet": Sound.shared.effect("flush")
        case "pill", "drink": Sound.shared.effect("gulp")
        default: break
        }
        finishCare(kid, gr.tool)
    }

    private func spawn(_ e: String, _ p: CGPoint, _ size: CGFloat, _ d: CGSize, _ dur: Double) {
        partSeq += 1
        let pt = Part(id: partSeq, emoji: e, p: p, d: d, size: size, born: Date(), dur: dur)
        parts.append(pt)
        if parts.count > 60 { parts.removeFirst(parts.count - 60) }
        DispatchQueue.main.asyncAfter(deadline: .now() + dur + 0.05) { parts.removeAll { $0.id == pt.id } }
    }

    private func rnd(_ a: CGFloat, _ b: CGFloat) -> CGFloat { CGFloat.random(in: a...b) }

    /** أنيميشن خاص لكل أداة أثناء استخدامها */
    private func animate(_ gr: inout Grip, _ idx: Int, dx: CGFloat, dy: CGFloat, dist: CGFloat, dt: Double) {
        let r = frameOf(idx), id = kids[idx].id
        let tp = tip(gr)
        let m = Face.mouth(id, in: r)
        gr.fxDist += dist
        gr.fxTime += dt
        let ms = Date().timeIntervalSinceReferenceDate
        switch gr.tool.kind {
        case "brush":
            // الفرشاة تميل مع اتجاه الحركة، ورغوة تتجمع عند الفم
            if dx > 0 { gr.rot = 18 } else if dx < 0 { gr.rot = -18 }
            if gr.fxDist > 26 {
                gr.fxDist = 0
                spawn("🫧", CGPoint(x: m.x + rnd(-26, 26), y: m.y + rnd(-14, 14)), 16 + rnd(0, 10), CGSize(width: rnd(-10, 10), height: -18), 0.9)
                gr.steps += 1
                if gr.steps % 2 == 0 { Sound.shared.effect("brush") }
            }
        case "eat":
            // قضمات: الأكل يصغر مع كل قضمة وتتطاير فتافيت
            let bites = Int(gr.progress * 4)
            if bites > gr.steps {
                gr.steps = bites
                Sound.shared.effect("bite")
                gr.scale = 1 - 0.18 * CGFloat(bites)
                for _ in 0..<3 { spawn(gr.tool.emoji, CGPoint(x: m.x + rnd(-16, 16), y: m.y + 6), 10, CGSize(width: rnd(-40, 40), height: 70), 0.6) }
            }
            gr.rot = sin(ms * 11) * 8
        case "drink":
            gr.rot = -55
            if gr.fxTime > 0.55 {
                gr.fxTime = 0
                Sound.shared.effect("gulp")
                spawn("💧", CGPoint(x: m.x + rnd(-10, 10), y: m.y), 14, CGSize(width: 0, height: 30), 0.5)
            }
        case "pill": gr.rot = -20
        case "thermo":
            gr.rot = -35
            gr.temp = 36.0 + min(1, gr.progress)
        case "scrub":
            if dx > 0 { gr.rot = 12 } else if dx < 0 { gr.rot = -12 }
            if gr.fxDist > 24 {
                gr.fxDist = 0
                spawn("🫧", CGPoint(x: tp.x + rnd(-20, 20), y: tp.y + rnd(-20, 20)), 18 + rnd(0, 12), CGSize(width: rnd(-12, 12), height: -30), 1.0)
                gr.steps += 1
                if gr.steps % 2 == 0 { Sound.shared.effect("bubble") }
            }
        case "shower":
            gr.rot = 0
            if gr.fxTime > 0.09 {
                gr.fxTime = 0
                spawn("💧", CGPoint(x: tp.x + rnd(-26, 26), y: tp.y + 20), 16, CGSize(width: rnd(-6, 6), height: 160), 0.65)
                gr.steps += 1
                if gr.steps % 5 == 0 { Sound.shared.effect("splash") }
            }
        case "toilet":
            // باب يغطي الشخصية حتى ينتهي
            door[id] = min(1, 0.3 + gr.progress)
        case "sleep":
            gr.rot = sin(ms * 2.5) * 10
            if gr.fxTime > 0.5 {
                gr.fxTime = 0
                let h = Face.head(id, in: r)
                spawn("💤", CGPoint(x: h.x + 20, y: h.y), 22, CGSize(width: 30, height: -50), 1.2)
            }
        case "hug":
            gr.rot = sin(ms * 6) * 12
            if gr.fxTime > 0.35 {
                gr.fxTime = 0
                spawn("💕", CGPoint(x: tp.x + rnd(-20, 20), y: tp.y - 20), 20, CGSize(width: rnd(-10, 10), height: -60), 1.0)
            }
        case "story":
            // صفحات تتقلب ونجوم خيال
            if gr.fxTime > 0.5 {
                gr.fxTime = 0
                gr.flip = -gr.flip
                Sound.shared.tap()
                spawn(["⭐", "🌈", "🦄", "🏰"].randomElement()!, CGPoint(x: tp.x + rnd(-30, 30), y: tp.y - 30), 20, CGSize(width: rnd(-20, 20), height: -50), 1.1)
            }
        case "ball":
            // كل ما ينعكس اتجاه الحركة العمودية تنط الكورة
            if dy != 0, gr.lastDy != 0, (dy > 0) != (gr.lastDy > 0), gr.fxDist > 30 {
                gr.fxDist = 0
                Sound.shared.effect("boing")
                spawn("✨", CGPoint(x: tp.x, y: tp.y + 30), 18, CGSize(width: 0, height: 10), 0.4)
            }
            if dy != 0 { gr.lastDy = dy }
            gr.rot += Double(dist) * 0.8
        case "balloon":
            gr.rot = sin(ms * 5) * 15
            if gr.fxDist > 60 {
                gr.fxDist = 0
                Sound.shared.effect("bubble")
                spawn(["🎊", "🎉", "✨"].randomElement()!, CGPoint(x: tp.x + rnd(-30, 30), y: tp.y - 30), 18, CGSize(width: rnd(-30, 30), height: -50), 0.9)
            }
        case "puzzle":
            let pieces = Int(gr.progress * 5)
            if pieces > gr.steps {
                gr.steps = pieces
                Sound.shared.tap()
                let c = Face.chest(id, in: r)
                spawn("🧩", tp, 22, CGSize(width: c.x - tp.x, height: c.y - tp.y), 0.5)
            }
            if dx > 0 { gr.rot = 10 } else if dx < 0 { gr.rot = -10 }
        case "car":
            // السيارة تلتفت لجهة الحركة ويطلع غبار وراها
            if dx > 1 { gr.flip = -1 } else if dx < -1 { gr.flip = 1 }
            if gr.fxDist > 50 {
                gr.fxDist = 0
                gr.steps += 1
                if gr.steps % 2 == 0 { Sound.shared.effect("zoom") }
                let back: CGFloat = gr.flip < 0 ? -40 : 40
                spawn("💨", CGPoint(x: tp.x + back, y: tp.y + 10), 18, CGSize(width: gr.flip < 0 ? -30 : 30, height: 0), 0.6)
            }
        default: break
        }
    }

    /** بعد اكتمال الاستخدام: يرتفع المؤشر وتتكلم الشخصية ويطلع أثر فوق الرأس */
    private func finishCare(_ kid: Kid, _ t: Tool) {
        store.addNeed(kid.id, t.meter, t.amount)
        Sound.shared.success()
        bump(kid.id)
        // عبارة تناسب المهمة اللي خلصت (أكل، تفريش، دش، لعب...)
        let line = Acts.doneLine(kid, t.kind) ?? Needs.react(kid, t.meter)
        chat.say(kid.id, line); Speaker.shared.say(line, as: kid.id)
        guard let idx = kids.firstIndex(where: { $0.id == kid.id }) else { return }
        let r = frameOf(idx)
        puffUp = false
        puffs = (0..<3).map { Puff(id: $0, emoji: Needs.effect(t.meter), p: CGPoint(x: r.minX + r.width * (0.25 + 0.25 * CGFloat($0)), y: r.minY + r.height * 0.1)) }
        DispatchQueue.main.async { withAnimation(.easeOut(duration: 0.9)) { puffUp = true } }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { puffs = [] }
    }

    // MARK: - حركة الشخصيات: المشي والأنشطة والسحب

    private func idx(_ id: String) -> Int? { kids.firstIndex { $0.id == id } }

    private func stepWalks(_ t: Date) {
        guard size != .zero else { return }
        for (id, w) in walks {
            let f = min(1, t.timeIntervalSince(w.start) / w.dur)
            pos[id] = CGPoint(x: w.from.x + (w.to.x - w.from.x) * f, y: w.from.y + (w.to.y - w.from.y) * f)
            if f >= 1 { walks[id] = nil; w.done() }
        }
        if t >= nextIdle {
            nextIdle = t.addingTimeInterval(Double.random(in: 8...15))
            idleTick()
        }
    }

    /** مشي واقعي: جانبي لما يمشي يمين أو يسار، ظهر لما يبعد، ووجه لما يقرب */
    private func walkTo(_ id: String, _ to: CGPoint, crawl: Bool, _ done: @escaping () -> Void) {
        guard let i = idx(id) else { return }
        let from = feet(i)
        let dx = to.x - from.x, dy = to.y - from.y
        let dist = hypot(dx, dy)
        walks[id] = nil
        if dist < 6 { done(); return }
        poses[id] = crawl ? "crawl" : abs(dx) >= abs(dy) * 0.8 ? "side" : dy < 0 ? "back" : "front"
        if abs(dx) > 2 { facing[id] = dx < 0 ? -1 : 1 }
        let speed: CGFloat = crawl ? 70 : 120
        walks[id] = Walk(from: from, to: to, start: Date(), dur: min(7, max(0.3, Double(dist / speed))), done: done)
    }

    private func idleTick() {
        guard grip == nil else { return }
        guard let kid = kids.filter({ !acting.contains($0.id) && dragging != $0.id }).randomElement(), let i = idx(kid.id) else { return }
        let (top, bot) = floorBand
        let half = frameOf(i).width / 2
        let id = kid.id
        guard let act = Acts.forKid(store, kid, room.id).randomElement() else {
            // ما فيه نشاط: يتمشى لمكان ثاني
            acting.insert(id)
            let g = (actGen[id] ?? 0) + 1; actGen[id] = g
            walkTo(id, CGPoint(x: CGFloat.random(in: half...(size.width - half)), y: CGFloat.random(in: top...bot)), crawl: false) {
                if actGen[id] == g { endAct(id) }
            }
            return
        }
        runAct(kid, act)
    }

    private func runAct(_ kid: Kid, _ act: Act) {
        let id = kid.id
        guard let i = idx(id) else { return }
        acting.insert(id)
        let g = (actGen[id] ?? 0) + 1; actGen[id] = g
        let ok = { actGen[id] == g }
        let (top, bot) = floorBand
        let crawl = act.pose == "crawl"
        let half = frameOf(i).width / 2
        let w = size.width
        let tx = min(max((act.fx < 0 ? CGFloat.random(in: 0.15...0.85) : act.fx) * w, half), w - half)
        let tf = act.fx < 0 ? CGFloat.random(in: top...bot) : top + (bot - top) * 0.12
        walkTo(id, CGPoint(x: tx, y: tf), crawl: crawl) {
            guard ok() else { return }
            if act.pre > 0 { poses[id] = "back" } // يدور للرف أو الثلاجة يطلع الغرض
            DispatchQueue.main.asyncAfter(deadline: .now() + act.pre) {
                guard ok() else { return }
                poses[id] = act.pose
                props[id] = act.prop
                actFaces[id] = act.face
                let pool = act.lines[Acts.group(kid)] ?? []
                guard !pool.isEmpty else { endAct(id); return }
                let line = pool[store.nextFromPool("act_\(id)_\(act.id)", pool.count)]
                chat.talk(id, line)
                if let m = act.meter { store.addNeed(id, m, 15) }
                for n in 0..<5 {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3 + Double(n) * 0.8) {
                        guard ok(), let j = idx(id) else { return }
                        if act.id == "read" { props[id] = n % 2 == 0 ? "📖" : "📃" } // تقليب الصفحات
                        let r = frameOf(j)
                        spawn(act.parts.randomElement() ?? "✨", CGPoint(x: r.midX + rnd(-30, 30), y: r.minY + r.height * (crawl ? 0.6 : 0.25)), 20, CGSize(width: rnd(-25, 25), height: -50), 1.0)
                    }
                }
                if crawl {
                    // يكمل الحبو ويدف السيارة لمكان ثاني
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
                        guard ok() else { return }
                        walkTo(id, CGPoint(x: CGFloat.random(in: half...(w - half)), y: CGFloat.random(in: top...bot)), crawl: true) { if ok() { endAct(id) } }
                    }
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 4.5) { if ok() { endAct(id) } }
                }
            }
        }
    }

    private func endAct(_ id: String) {
        acting.remove(id)
        props[id] = nil
        actFaces[id] = nil
        poses[id] = "front"
    }

    /** يوقف أي نشاط: لما يلمسه الطفل أو يسحبه أو يستخدم عليه أداة */
    private func cancelAct(_ id: String) {
        actGen[id] = (actGen[id] ?? 0) + 1
        if let w = walks[id] {
            let f = min(1, Date().timeIntervalSince(w.start) / w.dur)
            pos[id] = CGPoint(x: w.from.x + (w.to.x - w.from.x) * f, y: w.from.y + (w.to.y - w.from.y) * f)
            walks[id] = nil
        }
        endAct(id)
    }

    /** الطفل ينسحب لأي مكان، ولما ينترك ينزل على الأرضية؛ واللمسة تخليه يلتفت ويتكلم */
    private func kidDrag(_ kid: Kid) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("scene"))
            .onChanged { v in
                guard grip == nil, let i = idx(kid.id) else { return }
                if dragging != kid.id {
                    guard hypot(v.translation.width, v.translation.height) > 12 else { return }
                    cancelAct(kid.id)
                    dragFrom = feet(i)
                    dragging = kid.id
                    Sound.shared.pickUp()
                }
                guard let f0 = dragFrom else { return }
                let half = frameOf(i).width / 2
                pos[kid.id] = CGPoint(x: min(max(f0.x + v.translation.width, half), size.width - half),
                                      y: min(max(f0.y + v.translation.height, frameOf(i).height * 0.5), size.height))
            }
            .onEnded { v in
                if dragging == kid.id {
                    dragging = nil
                    dragFrom = nil
                    guard let i = idx(kid.id) else { return }
                    let (top, bot) = floorBand
                    let f = feet(i)
                    walks[kid.id] = Walk(from: f, to: CGPoint(x: f.x, y: min(max(f.y, top), bot)), start: Date(), dur: 0.26) { bump(kid.id) }
                } else if grip == nil, hypot(v.translation.width, v.translation.height) < 12 {
                    tapKid(kid)
                }
            }
    }
}
