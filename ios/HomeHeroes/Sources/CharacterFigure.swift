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

/** ملامح الوجه في رسم الجسم (بالبكسل الأصلي): العينان والفم ولون البشرة، لأنيميشن الأدوات */
struct Face {
    let eyeLX: CGFloat, eyeRX: CGFloat, eyeY: CGFloat, eyeR: CGFloat
    let mouthX: CGFloat, mouthY: CGFloat, mouthW: CGFloat, skin: UInt32

    static func of(_ id: String) -> Face {
        switch id {
        case "ayan": return Face(eyeLX: 102, eyeRX: 172, eyeY: 142, eyeR: 22, mouthX: 140, mouthY: 210, mouthW: 50, skin: 0xF9CA92)
        case "layan": return Face(eyeLX: 83, eyeRX: 148, eyeY: 107, eyeR: 17, mouthX: 118, mouthY: 161, mouthW: 44, skin: 0xF8DFC6)
        case "rayan": return Face(eyeLX: 83, eyeRX: 148, eyeY: 117, eyeR: 16, mouthX: 118, mouthY: 179, mouthW: 42, skin: 0xF4D5B7)
        default: return Face(eyeLX: 78, eyeRX: 146, eyeY: 122, eyeR: 16, mouthX: 111, mouthY: 191, mouthW: 42, skin: 0xEACCB1)
        }
    }

    /** موضع نقطة من الرسم الأصلي داخل إطار الشخصية على الشاشة */
    static func point(_ id: String, in r: CGRect, _ rx: CGFloat, _ ry: CGFloat) -> CGPoint {
        let rig = Rig.of(id)
        let s = min(r.height / rig.h, r.width / rig.w)
        return CGPoint(x: r.minX + (r.width - rig.w * s) / 2 + rx * s, y: r.maxY - rig.h * s + ry * s)
    }

    static func mouth(_ id: String, in r: CGRect) -> CGPoint { let f = of(id); return point(id, in: r, f.mouthX, f.mouthY) }
    static func head(_ id: String, in r: CGRect) -> CGPoint { let f = of(id); return point(id, in: r, Rig.of(id).w / 2, f.eyeY - 70) }
    static func chest(_ id: String, in r: CGRect) -> CGPoint { let g = Rig.of(id); return point(id, in: r, g.w / 2, g.legY * 0.72) }
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
    /** تعبير الوجه أثناء استخدام أداة: teeth، chew، open، sleep أو nil */
    var face: String? = nil
    /** وضعية الرسم: front أمام، side جانب، back ظهر، crawl حبو (أيان) */
    var pose: String = "front"
    /** اتجاه الوجه في الوضعية الجانبية: 1 يمين، -1 يسار */
    var facing: CGFloat = 1
    /** مرفوع بالإصبع: يتأرجح شوي */
    var held: Bool = false

    var body: some View {
        TimelineView(.animation) { tl in
            let ph = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 0.7) / 0.7
            Canvas { c, size in draw(c, size, CGFloat(ph) * 2 * .pi) }
        }
    }

    private func draw(_ c: GraphicsContext, _ size: CGSize, _ t: CGFloat) {
        let rig = Rig.of(kid.id)
        guard size.width > 0, size.height > 0 else { return }
        switch pose {
        case "crawl": drawCrawl(c, size, t); return
        case "side": drawFlat(c, size, t, "side_\(kid.id).png", flip: facing < 0); return
        case "back": drawFlat(c, size, t, "back_\(kid.id).png", flip: false); return
        default: break
        }
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
        b.rotate(by: .degrees(Double(bend * 10 + swing * 2 + (held ? sin(t) * 5 : 0))))
        b.translateBy(x: -rig.w / 2, y: -rig.legY)
        b.draw(b.resolve(Image(uiImage: image("body_\(kid.id).png")).resizable()),
               in: CGRect(x: 0, y: 0, width: rig.w, height: rig.h))

        // تعبير الوجه أثناء استخدام الأداة
        if let f = face { drawFace(b, f, t) }

        // الغرض المحمول أمام الصدر
        if let item = carrying {
            b.draw(Text(item).font(.system(size: rig.w * 0.42)), at: CGPoint(x: rig.w / 2, y: rig.legY * 0.74))
        }
    }
}

extension CharacterFigure {
    /** صورة جانبية أو خلفية كاملة: المشي بهزة ونطة خفيفة بدل تحريك الرجلين */
    fileprivate func drawFlat(_ c: GraphicsContext, _ size: CGSize, _ t: CGFloat, _ name: String, flip: Bool) {
        let img = image(name)
        guard img.size.width > 0, img.size.height > 0 else { return }
        let s = min(size.height / img.size.height, size.width / img.size.width)
        let w = img.size.width * s, h = img.size.height * s
        let bob: CGFloat = walking ? -abs(sin(t * 2)) * h * 0.025 : sin(t) * h * 0.004
        let squash: CGFloat = walking ? 1 - abs(sin(t * 2)) * 0.03 : 1
        var g = c
        g.translateBy(x: size.width / 2, y: size.height)
        if flip { g.scaleBy(x: -1, y: 1) }
        if held { g.translateBy(x: 0, y: -h); g.rotate(by: .degrees(Double(sin(t) * 6))); g.translateBy(x: 0, y: h) }
        g.scaleBy(x: 1, y: squash)
        g.draw(g.resolve(Image(uiImage: img).resizable()), in: CGRect(x: -w / 2, y: -h + bob, width: w, height: h))
        if let item = carrying {
            c.draw(Text(item).font(.system(size: w * 0.5)), at: CGPoint(x: size.width / 2 + (flip ? -w : w) * 0.3, y: size.height - h * 0.42))
        }
    }

    /** أيان يحبو ويدف سيارته */
    fileprivate func drawCrawl(_ c: GraphicsContext, _ size: CGSize, _ t: CGFloat) {
        let img = image("crawl_\(kid.id).png")
        guard img.size.width > 0, img.size.height > 0 else { return }
        let s = min(size.height * 0.5 / img.size.height, size.width / img.size.width)
        let w = img.size.width * s, h = img.size.height * s
        let lift: CGFloat = walking ? abs(sin(t * 2)) * h * 0.03 : 0
        var g = c
        g.translateBy(x: size.width / 2, y: size.height)
        if facing < 0 { g.scaleBy(x: -1, y: 1) }
        if walking { g.rotate(by: .degrees(Double(sin(t * 2) * 2.5))) }
        g.draw(g.resolve(Image(uiImage: img).resizable()), in: CGRect(x: -w / 2, y: -h - lift, width: w, height: h))
    }

    fileprivate func drawFace(_ g: GraphicsContext, _ mode: String, _ t: CGFloat) {
        let fc = Face.of(kid.id)
        let mx = fc.mouthX, my = fc.mouthY, mw = fc.mouthW
        switch mode {
        case "teeth":
            // ابتسامة عريضة تظهر فيها الأسنان
            let h = mw * 0.42
            let outer = CGRect(x: mx - mw * 0.62, y: my - h * 0.55, width: mw * 1.24, height: h * 1.1)
            g.fill(Path(roundedRect: outer, cornerRadius: h * 0.5), with: .color(Color(hex: 0x8C2F39)))
            let inner = outer.insetBy(dx: mw * 0.06, dy: h * 0.12)
            g.fill(Path(roundedRect: inner, cornerRadius: h * 0.4), with: .color(.white))
            var lines = Path()
            lines.move(to: CGPoint(x: inner.minX, y: inner.midY)); lines.addLine(to: CGPoint(x: inner.maxX, y: inner.midY))
            for i in 1...5 {
                let x = inner.minX + inner.width * CGFloat(i) / 6
                lines.move(to: CGPoint(x: x, y: inner.minY + 2)); lines.addLine(to: CGPoint(x: x, y: inner.maxY - 2))
            }
            g.stroke(lines, with: .color(Color(hex: 0xADB5BD)), lineWidth: mw * 0.03)
            g.stroke(Path(roundedRect: outer, cornerRadius: h * 0.5), with: .color(Color(hex: 0xC2555F)), lineWidth: mw * 0.07)
        case "chew", "open":
            let open = mode == "chew" ? 0.08 + 0.30 * abs(sin(t * 2)) : 0.34
            let w = mw * 0.34, h = mw * open
            let r = CGRect(x: mx - w, y: my - h / 2, width: w * 2, height: h)
            g.fill(Path(ellipseIn: r), with: .color(Color(hex: 0x7A1F2B)))
            if h > mw * 0.15 {
                g.fill(Path(ellipseIn: CGRect(x: mx - w * 0.6, y: my, width: w * 1.2, height: h / 2)), with: .color(Color(hex: 0xE8737F)))
            }
            g.stroke(Path(ellipseIn: r), with: .color(Color(hex: 0xC2555F)), lineWidth: mw * 0.06)
        case "sleep":
            // جفون مغمضة بلون البشرة وخط نوم
            for ex in [fc.eyeLX, fc.eyeRX] {
                let r = fc.eyeR
                g.fill(Path(ellipseIn: CGRect(x: ex - r * 1.35, y: fc.eyeY - r * 1.05, width: r * 2.7, height: r * 2.05)), with: .color(Color(hex: fc.skin)))
                var arc = Path()
                arc.addArc(center: CGPoint(x: ex, y: fc.eyeY - r * 0.2), radius: r * 1.0, startAngle: .degrees(20), endAngle: .degrees(160), clockwise: false)
                g.stroke(arc, with: .color(Color(hex: 0x4A3426)), style: StrokeStyle(lineWidth: r * 0.28, lineCap: .round))
            }
        default: break
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
