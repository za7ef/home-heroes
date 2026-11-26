import SwiftUI

struct KidView: View {
    let kid: Kid
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav

    var body: some View {
        let chores = store.chores(kid.id)
        let done = store.doneToday(kid.id)
        Page(title: "\(kid.name) البطل", back: .home) {
            HStack(spacing: 16) {
                CharacterFigure(kid: kid).frame(width: 110, height: 170)
                VStack(alignment: .leading, spacing: 4) {
                    Text("⭐ \(store.points(kid.id))").font(.system(size: 34, weight: .bold)).foregroundColor(kid.color)
                    Text("🏅 المستوى \(store.level(kid.id))").font(.system(size: 22, weight: .bold))
                    Text("👨‍👩‍👧‍👦 \(kid.relation)").foregroundColor(gray)
                    Text("📍 الحين في \(Rooms.byId(store.kidRoom(kid.id)).name)").foregroundColor(gray)
                }
                Spacer()
                Btn("🎁 المكافآت", orange) { nav.show(.rewards(kid.id)) }
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 28).fill(.white))
            .overlay(RoundedRectangle(cornerRadius: 28).stroke(kid.color, lineWidth: 3))

            ProgressView(value: Double(done), total: Double(max(1, chores.count))).tint(kid.color).padding(.vertical, 8)
            if done == chores.count && !chores.isEmpty {
                Text("🏆 خلّصت كل مهام اليوم! بطل!").font(.system(size: 24, weight: .bold)).foregroundColor(green).frame(maxWidth: .infinity)
            }
            ForEach(Rooms.all) { room in
                let list = chores.filter { $0.room == room.id }
                if !list.isEmpty {
                    Button {
                        store.setKidRoom(kid.id, room.id)
                        nav.show(.room(room.id))
                    } label: {
                        HStack {
                            Text("\(room.emoji) \(room.name)").font(.system(size: 20, weight: .bold)).foregroundColor(orange).frame(width: 150, alignment: .leading)
                            Text(list.map { c -> String in
                                let s = store.taskState(kid.id, c.id)
                                return c.emoji + (s == .pending ? "⏳" : s == .done ? "✅" : "")
                            }.joined(separator: "   ")).font(.system(size: 24))
                            Spacer()
                            Text("روح ←").font(.system(size: 17, weight: .bold)).foregroundColor(kid.color)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 20).fill(.white))
                        .overlay(RoundedRectangle(cornerRadius: 20).stroke(kid.color, lineWidth: 2))
                    }.buttonStyle(.plain)
                }
            }
        }
        .onAppear { if kid.autoSpeak { Speaker.shared.say("هلا \(kid.name)!") } }
    }
}

struct RewardsView: View {
    let kid: Kid
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav

    var body: some View {
        let pts = store.points(kid.id)
        let pending = store.requests().filter { $0.kidId == kid.id && $0.kind == "reward" }.map { $0.refId }
        Page(title: "🎁 مكافآت \(kid.name)", back: .kid(kid.id)) {
            Text("عندك ⭐ \(pts)").font(.system(size: 28, weight: .bold)).foregroundColor(kid.color).frame(maxWidth: .infinity)
            ForEach(store.rewards()) { r in
                HStack {
                    Text(r.emoji).font(.system(size: 44))
                    VStack(alignment: .leading) {
                        Text(r.title).font(.system(size: 22, weight: .bold))
                        Text("⭐ \(r.cost)").font(.system(size: 18, weight: .bold)).foregroundColor(kid.color)
                    }
                    Spacer()
                    if pending.contains(r.id) { Text("⏳").font(.system(size: 34)) }
                    else if pts >= r.cost {
                        Btn("أبغاها!", green) {
                            store.addRequest(Request(id: store.newRequestId(), kidId: kid.id, kind: "reward", refId: r.id, title: r.title, points: r.cost))
                            Speaker.shared.say("طلبت \(r.title). بانتظار موافقة الأهل")
                        }
                    } else { Text("باقي \(r.cost - pts)").foregroundColor(gray) }
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 22).fill(.white))
                .overlay(RoundedRectangle(cornerRadius: 22).stroke(kid.color, lineWidth: 2))
                .onTapGesture { Speaker.shared.say(r.title) }
            }
        }
    }
}

enum ParentGate {
    static func open(_ store: Store, _ nav: Nav) { nav.gate = true }
}

struct PinPrompt: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var pin = ""

    var body: some View {
        let creating = store.pin == nil
        ZStack {
            Color.black.opacity(0.4).ignoresSafeArea().onTapGesture { nav.gate = false }
            VStack(spacing: 14) {
                Text(creating ? "إنشاء رمز منطقة الأهل" : "منطقة الأهل").font(.system(size: 22, weight: .bold))
                SecureField(creating ? "رمز جديد (4 أرقام أو أكثر)" : "الرمز السري", text: $pin)
                    .keyboardType(.numberPad).multilineTextAlignment(.center).font(.system(size: 24))
                    .padding(10).background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xF1F3F5)))
                HStack {
                    Btn("دخول", green) {
                        if creating {
                            if pin.count >= 4 { store.pin = pin; nav.gate = false; nav.show(.parent) }
                            else { nav.flash("الرمز لازم يكون 4 أرقام أو أكثر") }
                        } else if pin == store.pin { nav.gate = false; nav.show(.parent) }
                        else { nav.flash("الرمز غلط") }
                        pin = ""
                    }
                    Btn("إلغاء", gray) { nav.gate = false }
                }
            }
            .padding(24).frame(width: 360)
            .background(RoundedRectangle(cornerRadius: 22).fill(.white))
        }
    }
}

struct ParentView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var pointsKid: Kid? = nil
    @State private var delta = ""
    @State private var newPin = ""
    @State private var askPin = false
    @State private var askReset = false

    private func approve(_ r: Request) {
        if r.kind == "chore" {
            store.setTaskState(r.kidId, r.refId, .done)
            store.addPoints(r.kidId, r.points)
        } else {
            if store.points(r.kidId) < r.points {
                nav.flash("نقاط \(Family.byId(r.kidId).name) لا تكفي لهذه المكافأة")
                return
            }
            store.addPoints(r.kidId, -r.points)
        }
        store.removeRequest(r.id)
        if store.checkFamilyBonus() { nav.flash("🎉 العيلة كلها فازت بتحدي اليوم! +10 لكل واحد") }
    }

    private func reject(_ r: Request) {
        if r.kind == "chore" { store.setTaskState(r.kidId, r.refId, .todo) }
        store.removeRequest(r.id)
    }

    var body: some View {
        let reqs = store.requests()
        Page(title: "🔒 منطقة الأهل", back: .home) {
            Text("بانتظار الاعتماد (\(reqs.count))").font(.system(size: 22, weight: .bold)).foregroundColor(orange)
            if reqs.isEmpty { Text("لا توجد طلبات حاليًا.").foregroundColor(gray) }
            ForEach(reqs) { r in
                let kid = Family.byId(r.kidId)
                HStack {
                    Avatar(kid: kid, size: 52)
                    VStack(alignment: .leading) {
                        Text(kid.name).font(.headline)
                        Text(r.kind == "chore" ? "مهمة: \(r.title) (+\(r.points))" : "مكافأة: \(r.title) (−\(r.points))")
                    }
                    Spacer()
                    Btn("✔", green, size: 22) { approve(r) }
                    Btn("✖", red, size: 22) { reject(r) }
                }
                .padding(10)
                .background(RoundedRectangle(cornerRadius: 18).fill(.white))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(kid.color, lineWidth: 2))
            }
            if reqs.count > 1 { Btn("اعتماد الكل", green, full: true) { reqs.forEach { approve($0) } } }

            Text("الإدارة").font(.system(size: 22, weight: .bold)).foregroundColor(orange).padding(.top, 14)
            ForEach(Family.kids) { kid in Btn("✏️ مهام \(kid.name)", kid.color, full: true) { nav.show(.editChores(kid.id)) } }
            Btn("🎁 تعديل المكافآت", orange, full: true) { nav.show(.editRewards) }
            Btn("⚙️ الإعدادات", gray, full: true) { nav.settingsBack = .parent; nav.show(.settings) }
            Menu {
                ForEach(Family.kids) { kid in Button("\(kid.name) (⭐ \(store.points(kid.id)))") { delta = ""; pointsKid = kid } }
            } label: {
                Text("➕➖ تعديل النقاط يدويًا").font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                    .padding(.vertical, 9).frame(maxWidth: .infinity)
                    .background(RoundedRectangle(cornerRadius: 18).fill(blue))
            }
            Btn("🔑 تغيير الرمز السري", gray, full: true) { newPin = ""; askPin = true }
            Btn("🗑️ تصفير النقاط", red, full: true) { askReset = true }
        }
        .alert("نقاط \(pointsKid?.name ?? "")", isPresented: Binding(get: { pointsKid != nil }, set: { if !$0 { pointsKid = nil } })) {
            TextField("مثال: 20 أو -10", text: $delta).keyboardType(.numbersAndPunctuation)
            Button("حفظ") {
                if let k = pointsKid, let d = Int(delta.trimmingCharacters(in: .whitespaces)) { store.addPoints(k.id, d) } else { nav.flash("رقم غير صالح") }
                pointsKid = nil
            }
            Button("إلغاء", role: .cancel) { pointsKid = nil }
        }
        .alert("تغيير الرمز السري", isPresented: $askPin) {
            SecureField("الرمز الجديد", text: $newPin).keyboardType(.numberPad)
            Button("حفظ") { if newPin.count >= 4 { store.pin = newPin; nav.flash("تم حفظ الرمز") } else { nav.flash("الرمز لازم يكون 4 أرقام أو أكثر") } }
            Button("إلغاء", role: .cancel) {}
        }
        .alert("تصفير النقاط؟", isPresented: $askReset) {
            Button("تصفير", role: .destructive) { store.resetAll() }
            Button("إلغاء", role: .cancel) {}
        } message: { Text("تُحذف نقاط ومستويات الجميع وطلبات الاعتماد. لا يمكن التراجع.") }
    }
}

/** نافذة إضافة أو تعديل مهمة أو مكافأة */
struct ItemEditor: View {
    let title: String
    let valueHint: String
    @State var emoji: String
    @State var name: String
    @State var value: String
    let onSave: (String, String, Int) -> Void
    let onCancel: () -> Void
    @EnvironmentObject var nav: Nav

    var body: some View {
        ZStack {
            Color.black.opacity(0.4).ignoresSafeArea()
            VStack(spacing: 10) {
                Text(title).font(.system(size: 22, weight: .bold))
                TextField("إيموجي", text: $emoji).font(.system(size: 28)).multilineTextAlignment(.center)
                TextField("الاسم", text: $name).font(.system(size: 20))
                TextField(valueHint, text: $value).keyboardType(.numberPad).font(.system(size: 20))
                HStack {
                    Btn("حفظ", green) {
                        let n = name.trimmingCharacters(in: .whitespaces)
                        let v = Int(value) ?? 0
                        if n.isEmpty || v <= 0 { nav.flash("اكتب اسمًا ورقمًا أكبر من صفر") }
                        else { onSave(emoji.trimmingCharacters(in: .whitespaces).isEmpty ? "⭐" : emoji, n, v) }
                    }
                    Btn("إلغاء", gray) { onCancel() }
                }
            }
            .textFieldStyle(.roundedBorder)
            .padding(24).frame(width: 420)
            .background(RoundedRectangle(cornerRadius: 22).fill(.white))
        }
    }
}

struct EditChoresView: View {
    let kid: Kid
    @EnvironmentObject var store: Store
    @State private var editing: Chore? = nil
    @State private var adding = false

    var body: some View {
        let list = store.chores(kid.id)
        ZStack {
            Page(title: "✏️ مهام \(kid.name)", back: .parent) {
                ForEach(list) { ch in
                    HStack {
                        Text("\(ch.emoji)  \(ch.title)  (⭐ \(ch.points))").font(.system(size: 18))
                        Spacer()
                        let rm = Rooms.byId(ch.room)
                        Btn("\(rm.emoji) \(rm.name)", orange, size: 15) {
                            var l = list
                            if let i = l.firstIndex(of: ch), let ri = Rooms.all.firstIndex(of: rm) {
                                l[i].room = Rooms.all[(ri + 1) % Rooms.all.count].id
                                store.saveChores(kid.id, l)
                            }
                        }
                        Btn("✏️", blue, size: 17) { editing = ch }
                        Btn("🗑️", red, size: 17) { store.saveChores(kid.id, list.filter { $0.id != ch.id }) }
                    }
                    .padding(8).background(RoundedRectangle(cornerRadius: 16).fill(.white))
                }
                Text("اضغط زر الغرفة لتغيير مكان المهمة في البيت.").foregroundColor(gray)
                Btn("➕ إضافة مهمة", green, full: true) { adding = true }
                Btn("↩️ إرجاع المهام الافتراضية", gray, full: true) { store.saveChores(kid.id, Family.defaultChores(kid.id)) }
            }
            if adding || editing != nil {
                let ch = editing
                ItemEditor(title: ch == nil ? "مهمة جديدة" : "تعديل مهمة", valueHint: "النقاط",
                           emoji: ch?.emoji ?? "⭐", name: ch?.title ?? "", value: "\(ch?.points ?? 10)",
                           onSave: { e, n, p in
                               var l = store.chores(kid.id)
                               if let c = ch, let i = l.firstIndex(where: { $0.id == c.id }) {
                                   l[i].emoji = e; l[i].title = n; l[i].points = p
                               } else {
                                   l.append(Chore(id: "\(kid.id)_\(Int(Date().timeIntervalSince1970))", emoji: e, title: n, points: p, room: store.kidRoom(kid.id)))
                               }
                               store.saveChores(kid.id, l)
                               adding = false; editing = nil
                           },
                           onCancel: { adding = false; editing = nil })
            }
        }
    }
}

struct EditRewardsView: View {
    @EnvironmentObject var store: Store
    @State private var editing: Reward? = nil
    @State private var adding = false

    var body: some View {
        let list = store.rewards()
        ZStack {
            Page(title: "🎁 المكافآت", back: .parent) {
                ForEach(list) { r in
                    HStack {
                        Text("\(r.emoji)  \(r.title)  (⭐ \(r.cost))").font(.system(size: 18))
                        Spacer()
                        Btn("✏️", blue, size: 17) { editing = r }
                        Btn("🗑️", red, size: 17) { store.saveRewards(list.filter { $0.id != r.id }) }
                    }
                    .padding(8).background(RoundedRectangle(cornerRadius: 16).fill(.white))
                }
                Btn("➕ إضافة مكافأة", green, full: true) { adding = true }
            }
            if adding || editing != nil {
                let r = editing
                ItemEditor(title: r == nil ? "مكافأة جديدة" : "تعديل مكافأة", valueHint: "السعر بالنجوم",
                           emoji: r?.emoji ?? "🎁", name: r?.title ?? "", value: "\(r?.cost ?? 50)",
                           onSave: { e, n, p in
                               var l = store.rewards()
                               if let x = r, let i = l.firstIndex(where: { $0.id == x.id }) {
                                   l[i].emoji = e; l[i].title = n; l[i].cost = p
                               } else {
                                   l.append(Reward(id: "r_\(Int(Date().timeIntervalSince1970))", emoji: e, title: n, cost: p))
                               }
                               store.saveRewards(l)
                               adding = false; editing = nil
                           },
                           onCancel: { adding = false; editing = nil })
            }
        }
    }
}

struct AboutView: View {
    @EnvironmentObject var nav: Nav
    var body: some View {
        Page(title: "ℹ️ حول التطبيق", back: .settings) {
            Text("أبطال البيت").font(.system(size: 30, weight: .bold)).foregroundColor(orange).frame(maxWidth: .infinity)
            Text("الإصدار \(Updater.localVersion) (البناء \(Updater.localBuild))").font(.system(size: 20)).frame(maxWidth: .infinity)
            Text("لعبة مهام منزلية للأطفال: مهام يومية، نجوم ومستويات، مكافآت، واعتماد من الأهل. تعمل دون إنترنت.").padding(.vertical, 10)
            Text("المطوّر: za7ef").bold()
            Link("https://github.com/za7ef", destination: URL(string: "https://github.com/za7ef")!).foregroundColor(blue)
            Text("شكر وتراخيص").font(.system(size: 20, weight: .bold)).foregroundColor(orange).padding(.top, 14)
            Text("الرموز التعبيرية من خط النظام في الجهاز. وجوه الشخصيات مأخوذة من رسومات خاصة بالأسرة، وأجسامها مرسومة برمجيًا. التطبيق مبني بـ Swift وSwiftUI.\n\nCopyright © 2026 za7ef")
            Btn("📜 سياسة الاستخدام", gray, full: true) { nav.show(.text("سياسة الاستخدام", Texts.terms)) }
            Btn("🔐 سياسة الخصوصية", gray, full: true) { nav.show(.text("سياسة الخصوصية", Texts.privacy)) }
        }
    }
}

struct TextPage: View {
    let title: String
    let body_: String
    init(title: String, body: String) { self.title = title; self.body_ = body }
    var body: some View {
        Page(title: title, back: .about) { Text(body_).font(.system(size: 18)).lineSpacing(6) }
    }
}

/** الإعدادات: الصوت، الحوارات التلقائية، البحث عن تحديث، وحول التطبيق */
struct SettingsView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Nav
    @State private var music = Sound.shared.musicOn
    @State private var musicVol = Double(Settings.musicVol)
    @State private var fx = Settings.fxOn
    @State private var fxVol = Double(Settings.fxVol)
    @State private var voice = Settings.voiceOn
    @State private var voiceVol = Double(Settings.voiceVol)
    @State private var talk = Settings.autoTalk
    @State private var checking = false

    var body: some View {
        Page(title: "⚙️ الإعدادات", back: nav.settingsBack) {
            Text("الصوت").font(.system(size: 22, weight: .bold)).foregroundColor(orange)
            row("🎵 موسيقى الخلفية", $music)
            slider("مستوى الموسيقى", $musicVol)
            row("🔔 أصوات المؤثرات", $fx)
            slider("مستوى المؤثرات", $fxVol)
            row("🗣️ أصوات الشخصيات", $voice)
            slider("مستوى أصوات الشخصيات", $voiceVol)
            Text("اللعب").font(.system(size: 22, weight: .bold)).foregroundColor(orange).padding(.top, 12)
            row("💬 حوارات تلقائية بين الشخصيات", $talk)
            Text("التطبيق").font(.system(size: 22, weight: .bold)).foregroundColor(orange).padding(.top, 12)
            Btn(checking ? "⏳ جاري البحث…" : "🔄 البحث عن تحديث", blue, full: true) { checkUpdate() }
            Btn("ℹ️ حول التطبيق", gray, full: true) { nav.show(.about) }
            Text("الإصدار \(Updater.localVersion) (البناء \(Updater.localBuild))").foregroundColor(gray).frame(maxWidth: .infinity)
        }
        .onChange(of: music) { v in Sound.shared.musicOn = v }
        .onChange(of: musicVol) { v in Settings.musicVol = Int(v); Sound.shared.applyVolumes() }
        .onChange(of: fx) { v in Settings.fxOn = v }
        .onChange(of: fxVol) { v in Settings.fxVol = Int(v); Sound.shared.applyVolumes() }
        .onChange(of: voice) { v in Settings.voiceOn = v; if !v { Speaker.shared.stop() } }
        .onChange(of: voiceVol) { v in Settings.voiceVol = Int(v) }
        .onChange(of: talk) { v in Settings.autoTalk = v }
    }

    private func row(_ label: String, _ on: Binding<Bool>) -> some View {
        Toggle(isOn: on) { Text(label).font(.system(size: 20, weight: .bold)) }
            .padding(.horizontal, 16).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
    }

    private func slider(_ label: String, _ v: Binding<Double>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(label): \(Int(v.wrappedValue))%").font(.system(size: 17, weight: .bold)).foregroundColor(Color(hex: 0x495057))
            Slider(value: v, in: 0...100, step: 1)
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
    }

    /** البحث عن إصدار أحدث في GitHub؛ في الآيفون يفتح صفحة الإصدار لأن التطبيق المثبت يدويًا لا يحدّث نفسه */
    private func checkUpdate() {
        guard !checking else { return }
        checking = true
        Task { @MainActor in
            defer { checking = false }
            do {
                let info = try await Updater.fetchLatest()
                if Updater.isNewer(info.version, Updater.localVersion) {
                    store.skippedVersion = ""
                    nav.flash("يوجد إصدار جديد \(info.version)")
                    if let u = URL(string: info.url) { _ = await UIApplication.shared.open(u) }
                } else { nav.flash("الإصدار الحالي هو الأحدث (\(Updater.localVersion))") }
            } catch { nav.flash("تعذّر التحقق: \(error.localizedDescription)") }
        }
    }
}
