import AVFoundation

/**
 * أصوات اللعبة مولّدة داخل التطبيق (بدون ملفات صوت خارجية):
 * موسيقى خلفية هادئة تتكرر، وأصوات قصيرة للضغط والنجاح والالتقاط والخطأ.
 * لا تؤثر على صوت النطق العربي.
 */
final class Sound {
    static let shared = Sound()

    private let engine = AVAudioEngine()
    private let musicNode = AVAudioPlayerNode()
    private let fxNode = AVAudioPlayerNode()
    private let rate: Double = 22050
    private lazy var format = AVAudioFormat(standardFormatWithSampleRate: rate, channels: 1)!
    private var ready = false
    private var musicBuffer: AVAudioPCMBuffer?
    private var fx: [String: AVAudioPCMBuffer] = [:]

    var musicOn: Bool {
        get { UserDefaults.standard.object(forKey: "music_on") as? Bool ?? true }
        set {
            UserDefaults.standard.set(newValue, forKey: "music_on")
            newValue ? startMusic() : stopMusic()
        }
    }

    private init() {}

    private func setup() {
        if ready { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        engine.attach(musicNode)
        engine.attach(fxNode)
        engine.connect(musicNode, to: engine.mainMixerNode, format: format)
        engine.connect(fxNode, to: engine.mainMixerNode, format: format)
        applyVolumes()
        do { try engine.start() } catch { return }
        ready = true
        musicBuffer = makeMusic()
        fx["tap"] = makeNotes([(1046.5, 0.06)], wave: 0)
        fx["pick"] = makeNotes([(659.3, 0.06), (880, 0.07)], wave: 0)
        fx["success"] = makeNotes([(523.3, 0.10), (659.3, 0.10), (784, 0.10), (1046.5, 0.28)], wave: 1)
        fx["wrong"] = makeNotes([(392, 0.14), (311.1, 0.22)], wave: 1)
        fx["brush"] = makeNoise(0.14, decay: 18, smooth: 0.55, amp: 0.35)
        fx["bite"] = makeNoise(0.08, decay: 40, smooth: 0.25, amp: 0.6)
        fx["gulp"] = makeSweep(320, 140, 0.12, amp: 0.5)
        fx["bubble"] = makeSweep(500, 1300, 0.07, amp: 0.35)
        fx["splash"] = makeNoise(0.35, decay: 7, smooth: 0.7, amp: 0.3)
        fx["flush"] = makeNoise(0.9, decay: 3, smooth: 0.85, amp: 0.35)
        fx["boing"] = makeSweep(180, 520, 0.18, amp: 0.5)
        fx["zoom"] = makeSweep(260, 620, 0.22, amp: 0.3)
    }

    /** يطبّق مستويات الصوت من الإعدادات */
    func applyVolumes() {
        musicNode.volume = Float(0.36 * Double(Settings.musicVol) / 100)
        fxNode.volume = Float(0.75 * Double(Settings.fxVol) / 100)
    }

    /** ضجيج مُنعَّم (فرشاة، ماء، قضمة) */
    private func makeNoise(_ dur: Double, decay: Double, smooth: Double, amp: Double) -> AVAudioPCMBuffer? {
        let frames = AVAudioFrameCount(dur * rate)
        guard let buf = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames), let ch = buf.floatChannelData?[0] else { return nil }
        buf.frameLength = frames
        var gen = SystemRandomNumberGenerator()
        var y = 0.0
        for k in 0..<Int(frames) {
            let t = Double(k) / rate
            y = smooth * y + (1 - smooth) * Double.random(in: -1...1, using: &gen)
            let env = min(1, t / 0.01) * exp(-decay * t)
            ch[k] = Float(max(-1, min(1, y * env * amp * 2.2)))
        }
        return buf
    }

    /** نغمة تنزلق من تردد لآخر (فقاعة، بلعة، نطة) */
    private func makeSweep(_ f0: Double, _ f1: Double, _ dur: Double, amp: Double) -> AVAudioPCMBuffer? {
        let frames = AVAudioFrameCount(dur * rate)
        guard let buf = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames), let ch = buf.floatChannelData?[0] else { return nil }
        buf.frameLength = frames
        var ph = 0.0
        for k in 0..<Int(frames) {
            let t = Double(k) / rate
            ph += 2 * Double.pi * (f0 + (f1 - f0) * t / dur) / rate
            let env = min(1, t / 0.005) * exp(-4 * t / dur)
            ch[k] = Float(sin(ph) * env * amp)
        }
        return buf
    }

    // MARK: - توليد الصوت

    /** نغمات متتالية: wave 0 = جيبية ناعمة، 1 = مثلثية خفيفة */
    private func makeNotes(_ notes: [(Double, Double)], wave: Int, gap: Double = 0.01) -> AVAudioPCMBuffer? {
        let total = notes.reduce(0) { $0 + $1.1 + gap }
        let frames = AVAudioFrameCount(total * rate)
        guard let buf = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames), let ch = buf.floatChannelData?[0] else { return nil }
        buf.frameLength = frames
        var i = 0
        for (f, d) in notes {
            let n = Int(d * rate)
            for k in 0..<n where i + k < Int(frames) {
                let t = Double(k) / rate
                let env = min(1, t / 0.008) * exp(-3.5 * t / d)
                let ph = 2 * Double.pi * f * t
                let v = wave == 0 ? sin(ph) : (2 / Double.pi) * asin(sin(ph))
                ch[i + k] = Float(v * env * 0.5)
            }
            i += n + Int(gap * rate)
        }
        return buf
    }

    /** لحن خلفية بسيط (8 مازورات) مع باص ناعم، يتكرر بدون توقف */
    private func makeMusic() -> AVAudioPCMBuffer? {
        let beat = 0.32
        let melody: [Double] = [
            523.3, 659.3, 784, 659.3, 587.3, 698.5, 880, 698.5,
            523.3, 659.3, 784, 1046.5, 987.8, 784, 659.3, 587.3,
            440, 523.3, 659.3, 523.3, 493.9, 587.3, 784, 587.3,
            523.3, 659.3, 587.3, 493.9, 523.3, 0, 523.3, 0,
        ]
        let bass: [Double] = [130.8, 146.8, 130.8, 196, 110, 123.5, 130.8, 130.8]
        let frames = AVAudioFrameCount(Double(melody.count) * beat * rate)
        guard let buf = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames), let ch = buf.floatChannelData?[0] else { return nil }
        buf.frameLength = frames
        let n = Int(beat * rate)
        for (bi, f) in melody.enumerated() {
            let bassF = bass[(bi / 4) % bass.count]
            for k in 0..<n {
                let idx = bi * n + k
                if idx >= Int(frames) { break }
                let t = Double(k) / rate
                let env = min(1, t / 0.01) * exp(-4 * t)
                var v = f > 0 ? sin(2 * Double.pi * f * t) * env * 0.35 : 0
                let tb = Double((bi % 4) * n + k) / rate
                v += sin(2 * Double.pi * bassF * tb) * 0.18 * exp(-1.2 * tb)
                ch[idx] = Float(v)
            }
        }
        return buf
    }

    // MARK: - التشغيل

    func startMusic() {
        guard musicOn else { return }
        setup()
        guard ready, let b = musicBuffer, !musicNode.isPlaying else { return }
        musicNode.scheduleBuffer(b, at: nil, options: .loops)
        musicNode.play()
    }

    func stopMusic() {
        musicNode.stop()
    }

    private func play(_ name: String) {
        guard Settings.fxOn else { return }
        setup()
        guard ready, let b = fx[name] else { return }
        fxNode.scheduleBuffer(b, at: nil, options: .interrupts)
        if !fxNode.isPlaying { fxNode.play() }
    }

    func tap() { play("tap") }
    func pickUp() { play("pick") }
    func success() { play("success") }
    func wrong() { play("wrong") }
    /** صوت مؤثر باسمه: brush، bite، gulp، bubble، splash، flush، boing، zoom */
    func effect(_ name: String) { play(name) }
}
