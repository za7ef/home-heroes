import SwiftUI

/** أبعاد رسم الشخصية بالبكسل الأصلي: العرض، الارتفاع، ومستوى الورك (بداية الرجلين) */
struct Rig {
    let w: CGFloat, h: CGFloat, legY: CGFloat

    static func of(_ id: String) -> Rig {
        switch id {
        case "ayan": return Rig(w: 267, h: 640, legY: 475)
        case "layan": return Rig(w: 235, h: 640, legY: 455)
        case "rayan": return Rig(w: 232, h: 640, legY: 460)
        default: return Rig(w: 235, h: 640, legY: 458)
        }
    }
}

/**
 * شخصية مرسومة بأسلوب واحد: الوجه من الصورة المرجعية للطفل والجسم بنفس الأسلوب وبملابس الإصدار 1.0.
 * الرجلان منفصلتان حتى تتحركا أثناء المشي، والجسم ينحني عند الالتقاط.
 */
struct CharacterFigure: View {
    let kid: Kid
    var walking: Bool = false
    var carrying: String? = nil
    var bend: CGFloat = 0

    var body: some View {
        TimelineView(.animation) { tl in
            let ph = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 0.7) / 0.7
            Canvas { c, size in draw(c, size, CGFloat(ph) * 2 * .pi) }
        }
    }

    private func draw(_ c: GraphicsContext, _ size: CGSize, _ t: CGFloat) {
        let rig = Rig.of(kid.id)
        guard size.width > 0, size.height > 0 else { return }
        let s = min(size.height / rig.h, size.width / rig.w)
        var g = c
        g.translateBy(x: (size.width - rig.w * s) / 2, y: size.height - rig.h * s)
        g.scaleBy(x: s, y: s)
        let swing: CGFloat = walking ? sin(t) : 0
        let bob: CGFloat = walking ? -abs(sin(t)) * rig.h * 0.02 : sin(t) * rig.h * 0.004
        let legH = rig.h - rig.legY

        // الرجلان: كل رجل ترتفع بالتناوب أثناء المشي
        for (name, side) in [("legl", CGFloat(-1)), ("legr", CGFloat(1))] {
            let x0: CGFloat = side < 0 ? 0 : rig.w / 2
            let lift = walking ? max(0, side * swing) * legH * 0.16 : 0
            var lg = g
            lg.translateBy(x: x0 + rig.w / 4, y: rig.legY)
            lg.rotate(by: .degrees(Double(swing * 5)))
            lg.translateBy(x: -(x0 + rig.w / 4), y: -rig.legY)
            lg.draw(lg.resolve(Image(uiImage: image("\(name)_\(kid.id).png")).resizable()),
                    in: CGRect(x: x0, y: rig.legY - lift, width: rig.w / 2, height: legH))
        }

        // الجسم والرأس: يهتز مع المشي، وينحني للأمام عند الالتقاط
        var b = g
        b.translateBy(x: 0, y: bob + bend * rig.h * 0.05)
        b.translateBy(x: rig.w / 2, y: rig.legY)
        b.rotate(by: .degrees(Double(bend * 10 + swing * 2)))
        b.translateBy(x: -rig.w / 2, y: -rig.legY)
        b.draw(b.resolve(Image(uiImage: image("body_\(kid.id).png")).resizable()),
               in: CGRect(x: 0, y: 0, width: rig.w, height: rig.h))

        // الغرض المحمول أمام الصدر
        if let item = carrying {
            b.draw(Text(item).font(.system(size: rig.w * 0.42)), at: CGPoint(x: rig.w / 2, y: rig.legY * 0.74))
        }
    }
}

/** صورة وجه الطفل داخل دائرة بلونه */
struct Avatar: View {
    let kid: Kid
    var size: CGFloat = 44
    var body: some View {
        Image(uiImage: image(kid.face)).resizable().scaledToFit()
            .padding(size * 0.08)
            .background(Circle().fill(.white))
            .overlay(Circle().stroke(kid.color, lineWidth: 3))
            .frame(width: size, height: size)
    }
}
