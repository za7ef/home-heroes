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

/** أداة قيد الاستخدام على شخصية (تتحرك عند الفم أو الرأس أو اليد) */
struct Using: Equatable { let kid: String; let emoji: String; let spot: String }

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
    @State private var ghost: (String, CGPoint)? = nil
    @State private var using: Using? = nil
    @State private var wob = false
    @State private var bounceKid: String? = nil
    @State private var puffs: [Puff] = []
    @State private var puffUp = false
    @State private var now = Date()
    private let timer = Timer.publish(every: 4, on: .main, in: .common).autoconnect()

    private let trayH: CGFloat = 76
    private var art: RoomArt { RoomItems.art(room.id) }
    private var scale: CGFloat { size.width / art.iw }
    private var offY: CGFloat { size.height - art.ih * scale }
    private var groundY: CGFloat { size.height - trayH - 4 }

    private func charH(_ id: String) -> CGFloat {
        size.height * (id == "ayan" ? 0.30 : id == "layan" ? 0.36 : id == "rayan" ? 0.41 : 0.45)
    }

    private func footX(_ idx: Int) -> CGFloat { size.width * CGFloat(idx + 1) / CGFloat(kids.count + 1) }

    private func frameOf(_ idx: Int) -> CGRect {
        let h = charH(kids[idx].id), w = h * 0.45
        return CGRect(x: footX(idx) - w / 2, y: groundY - h, width: w, height: h)
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
                    ForEach(Array(kids.enumerated()), id: \.1.id) { idx, kid in
                        let r = frameOf(idx)
                        CharacterFigure(kid: kid, walking: false, carrying: nil, bend: bounceKid == kid.id ? 0.5 : 0)
                            .frame(width: r.width, height: r.height)
                            .overlay(alignment: .top) {
                                if let l = chat.lines[kid.id] {
                                    Bubble(text: l).environment(\.layoutDirection, .rightToLeft).offset(y: -34)
                                }
                            }
                            .scaleEffect(selected == kid.id ? 1.06 : 1, anchor: .bottom)
                            .position(x: r.midX, y: r.midY)
                            .onTapGesture { tapKid(kid) }
                        if let m = Needs.lowest(store, kid.id), chat.lines[kid.id] == nil {
                            Text(m.emoji).font(.system(size: 26))
                                .frame(width: 48, height: 48)
                                .background(Circle().fill(.white)).overlay(Circle().stroke(red, lineWidth: 3))
                                .rotationEffect(.degrees(wob ? 10 : -10))
                                .position(x: r.midX, y: max(r.minY - 30, 70))
                                .onTapGesture { tapKid(kid) }
                        }
                        if let u = using, u.kid == kid.id {
                            let fy: CGFloat = u.spot == "mouth" ? 0.17 : u.spot == "head" ? 0.02 : 0.45
                            let fx: CGFloat = u.spot == "hand" ? 0.85 : 0.5
                            Text(u.emoji).font(.system(size: 44))
                                .rotationEffect(.degrees(wob ? 25 : -25))
                                .offset(x: wob ? 8 : -8)
                                .position(x: r.minX + r.width * fx, y: r.minY + r.height * fy)
                                .allowsHitTesting(false)
                        }
                    }
                    ForEach(puffs) { p in
                        Text(p.emoji).font(.system(size: 26))
                            .position(x: p.p.x, y: p.p.y - (puffUp ? 70 : 0))
                            .opacity(puffUp ? 0 : 1)
                            .allowsHitTesting(false)
                    }
                    tray.position(x: size.width / 2, y: size.height - trayH / 2 - 2)
                    if let gh = ghost {
                        Text(gh.0).font(.system(size: 52)).opacity(0.9).position(x: gh.1.x, y: gh.1.y - 50).allowsHitTesting(false)
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
                                if ghost == nil { Sound.shared.tap() }
                                ghost = (t.emoji, v.location)
                            }
                            .onEnded { v in
                                ghost = nil
                                if hypot(v.translation.width, v.translation.height) < 20 { Speaker.shared.say(t.name); return }
                                let p = CGPoint(x: v.location.x, y: v.location.y - 50)
                                if let i = kids.indices.first(where: { frameOf($0).insetBy(dx: -24, dy: -24).contains(p) }) { care(kids[i], t) }
                                else { Speaker.shared.say("اسحبها على واحد من العيال") }
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
                                    Capsule().fill(v < Needs.low ? red : v < 65 ? Color(hex: 0xF59F00) : green)
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
        chat.start(visible: { kids }, roomOf: { _ in room.id })
    }

    private func tapKid(_ kid: Kid) {
        Sound.shared.tap()
        selected = kid.id
        bump(kid.id)
        let line = Needs.lowest(store, kid.id).map { Needs.needLine(store, kid, $0) } ?? (Talk.solo(kid, room.id).randomElement() ?? "")
        chat.say(kid.id, line)
        Speaker.shared.say(line, as: kid.id)
    }

    private func bump(_ id: String) {
        withAnimation(.easeOut(duration: 0.12)) { bounceKid = id }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { withAnimation(.easeIn(duration: 0.15)) { if bounceKid == id { bounceKid = nil } } }
    }

    /** استخدام الأداة على الشخصية ثم رفع المؤشر */
    private func care(_ kid: Kid, _ t: Tool) {
        guard using == nil else { return }
        selected = kid.id
        if store.need(kid.id, t.meter) >= 95 {
            Sound.shared.wrong()
            let l = Needs.full(kid)
            chat.say(kid.id, l); Speaker.shared.say(l, as: kid.id)
            return
        }
        using = Using(kid: kid.id, emoji: t.emoji, spot: t.spot)
        bump(kid.id)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            using = nil
            store.addNeed(kid.id, t.meter, t.amount)
            Sound.shared.success()
            let line = Needs.react(kid, t.meter)
            chat.say(kid.id, line); Speaker.shared.say(line, as: kid.id)
            guard let idx = kids.firstIndex(where: { $0.id == kid.id }) else { return }
            let r = frameOf(idx)
            puffUp = false
            puffs = (0..<3).map { Puff(id: $0, emoji: Needs.effect(t.meter), p: CGPoint(x: r.minX + r.width * (0.25 + 0.25 * CGFloat($0)), y: r.minY + r.height * 0.1)) }
            DispatchQueue.main.async { withAnimation(.easeOut(duration: 0.9)) { puffUp = true } }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { puffs = [] }
        }
    }
}
