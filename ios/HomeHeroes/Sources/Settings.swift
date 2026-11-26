import Foundation

/** إعدادات الصوت واللعب، محفوظة على الجهاز فقط */
enum Settings {
    private static let d = UserDefaults.standard
    private static func b(_ k: String, _ def: Bool) -> Bool { d.object(forKey: k) as? Bool ?? def }
    private static func i(_ k: String, _ def: Int) -> Int { d.object(forKey: k) as? Int ?? def }

    /** أصوات المؤثرات (الضغط، النجاح، الفرشاة، الماء...) */
    static var fxOn: Bool { get { b("fx_on", true) } set { d.set(newValue, forKey: "fx_on") } }
    /** أصوات الشخصيات المسجلة */
    static var voiceOn: Bool { get { b("voice_on", true) } set { d.set(newValue, forKey: "voice_on") } }
    /** الحوارات التلقائية بين الشخصيات */
    static var autoTalk: Bool { get { b("auto_talk", true) } set { d.set(newValue, forKey: "auto_talk") } }
    /** مستويات الصوت من 0 إلى 100 */
    static var musicVol: Int { get { i("music_vol", 60) } set { d.set(min(100, max(0, newValue)), forKey: "music_vol") } }
    static var fxVol: Int { get { i("fx_vol", 80) } set { d.set(min(100, max(0, newValue)), forKey: "fx_vol") } }
    static var voiceVol: Int { get { i("voice_vol", 100) } set { d.set(min(100, max(0, newValue)), forKey: "voice_vol") } }
}
