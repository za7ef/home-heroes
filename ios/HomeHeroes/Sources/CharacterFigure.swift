import SwiftUI

struct Outfit {
    let shirt: Color, trim: Color, pants: Color
    let shortSleeves: Bool, shorts: Bool, stripes: Bool, hoodie: Bool

    static func of(_ id: String) -> Outfit {
        switch id {
        case "abdulrahman": return Outfit(shirt: Color(hex: 0xD9342B), trim: Color(hex: 0xA8231C), pants: Color(hex: 0x2F3E5C), shortSleeves: true, shorts: false, stripes: false, hoodie: false)
        case "rayan": return Outfit(shirt: Color(hex: 0xF7F3EA), trim: Color(hex: 0xCFC6B4), pants: Color(hex: 0x6B5B4B), shortSleeves: true, shorts: false, stripes: false, hoodie: false)
        case "layan": return Outfit(shirt: Color(hex: 0x4F8FD0), trim: .white, pants: Color(hex: 0x8E6BB8), shortSleeves: false, shorts: false, stripes: false, hoodie: true)
        default: return Outfit(shirt: Color(hex: 0xDCEBFA), trim: Color(hex: 0x5C8FD6), pants: Color(hex: 0x3B6FB6), shortSleeves: true, shorts: true, stripes: true, hoodie: false)
        }
    }
}

/** شخصية كرتونية: الوجه من صورة الطفل، والجسم مرسوم ويتحرك أثناء المشي والحمل */
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
        let w = size.width, h = size.height
        guard w > 0, h > 0 else { return }
        let o = Outfit.of(kid.id)
        let skin = Color(hex: 0xE7B48A), line = Color(hex: 0x3B2A20)
        let sw = max(1.5, h * 0.012)
        let headD = min(w * 0.92, h * (kid.toddler ? 0.5 : 0.42))
        let legLen = h * (kid.toddler ? 0.17 : 0.23)
        let footY = h - sw * 2
        let bob = walking ? -abs(sin(t)) * h * 0.035 : sin(t) * h * 0.006
        let hipY = footY - legLen + bob
        let torsoH = (h - headD * 0.9 - legLen - sw * 2) + bend * h * 0.06
        let shoulderY = hipY - torsoH + bend * h * 0.08
        let cx = w / 2
        let torsoW = headD * (kid.toddler ? 0.78 : 0.72)
        let legW = torsoW * 0.32, armW = legW * 0.8
        let swing = walking ? sin(t) : 0

        // الأرجل
        for side in [-1.0, 1.0] as [CGFloat] {
            let lift = walking ? max(0, side * swing) * legLen * 0.22 : 0
            let lx = cx + side * torsoW * 0.22
            let top = hipY - sw, bottom = footY - lift
            let pantsEnd = o.shorts ? top + (bottom - top) * 0.4 : bottom - legW * 0.3
            if o.shorts {
                let r = Path(roundedRect: CGRect(x: lx - legW / 2, y: top, width: legW, height: bottom - top), cornerRadius: legW / 2)
                c.fill(r, with: .color(skin)); c.stroke(r, with: .color(line), lineWidth: sw)
            }
            let p = Path(roundedRect: CGRect(x: lx - legW / 2 - sw, y: top, width: legW + 2 * sw, height: pantsEnd - top), cornerRadius: legW / 3)
            c.fill(p, with: .color(o.pants)); c.stroke(p, with: .color(line), lineWidth: sw)
            c.fill(Path(ellipseIn: CGRect(x: lx - legW * 0.75, y: bottom - legW * 0.55, width: legW * 1.5, height: legW * 0.55 + sw)), with: .color(Color(hex: 0x343A40)))
        }

        // الجسم
        let torso = Path(roundedRect: CGRect(x: cx - torsoW / 2, y: shoulderY, width: torsoW, height: hipY + sw - shoulderY), cornerRadius: torsoW * 0.28)
        c.fill(torso, with: .color(o.shirt))
        if o.stripes {
            var y = shoulderY + torsoH * 0.18
            while y < hipY - sw {
                c.fill(Path(CGRect(x: cx - torsoW / 2 + sw, y: y, width: torsoW - 2 * sw, height: torsoH * 0.06)), with: .color(o.trim))
                y += torsoH * 0.18
            }
        }
        if o.hoodie {
            c.fill(Path(CGRect(x: cx - torsoW * 0.2, y: shoulderY, width: torsoW * 0.4, height: hipY - shoulderY)), with: .color(o.trim))
        }
        c.stroke(torso, with: .color(line), lineWidth: sw)

        // الأيدي
        let armLen = torsoH * 0.9
        let holding = carrying != nil
        for side in [-1.0, 1.0] as [CGFloat] {
            let sx = cx + side * (torsoW / 2 - armW * 0.2), sy = shoulderY + armW * 0.6
            let ang: CGFloat = holding ? side * -0.95 : (walking ? side * 0.15 + swing * 0.55 * side : side * (0.12 + sin(t) * 0.03))
            let ex = sx + sin(ang) * armLen * (holding ? 0.85 : 1)
            let ey = sy + cos(ang) * armLen * (holding ? 0.7 : 1)
            var arm = Path(); arm.move(to: CGPoint(x: sx, y: sy)); arm.addLine(to: CGPoint(x: ex, y: ey))
            c.stroke(arm, with: .color(line), style: StrokeStyle(lineWidth: armW + sw * 2, lineCap: .round))
            c.stroke(arm, with: .color(o.shortSleeves ? skin : o.shirt), style: StrokeStyle(lineWidth: armW, lineCap: .round))
            if o.shortSleeves {
                var sl = Path(); sl.move(to: CGPoint(x: sx, y: sy)); sl.addLine(to: CGPoint(x: sx + (ex - sx) * 0.3, y: sy + (ey - sy) * 0.3))
                c.stroke(sl, with: .color(o.shirt), style: StrokeStyle(lineWidth: armW, lineCap: .round))
            }
            c.fill(Path(ellipseIn: CGRect(x: ex - armW * 0.62, y: ey - armW * 0.62, width: armW * 1.24, height: armW * 1.24)), with: .color(skin))
        }

        // الرأس
        let headCy = shoulderY - headD * 0.42 + bend * h * 0.03
        let hx = cx + (walking ? sin(t) * w * 0.01 : 0)
        let ring = headD / 2 + sw * 1.6
        c.fill(Path(ellipseIn: CGRect(x: hx - ring, y: headCy - ring, width: ring * 2, height: ring * 2)), with: .color(kid.color))
        let img = c.resolve(Image(uiImage: image(kid.face)).resizable())
        c.draw(img, in: CGRect(x: hx - headD / 2, y: headCy - headD / 2, width: headD, height: headD))

        // الغرض المحمول
        if let item = carrying {
            c.draw(Text(item).font(.system(size: torsoW * 0.95)), at: CGPoint(x: cx, y: shoulderY + torsoH * 0.3))
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
