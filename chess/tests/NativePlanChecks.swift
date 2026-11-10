import Foundation
import CoreGraphics
@main struct NativePlanChecks {
    static var checks: [String] = []
    static func check(_ value: Bool, _ label: String) throws {
        if !value { throw NSError(domain: "NativePlanChecks", code: 1, userInfo: [NSLocalizedDescriptionKey: label]) };checks.append(label)
    }
    static func snapshot(_ values: [(String, String, String)], focused: Bool = true) -> ChessNativePlan.Snapshot {
        var nodes: [ChessNativePlan.Node] = []
        for (i, item) in values.enumerated() {
            let frame = CGRect(x: 20, y: CGFloat(40 + i * 30), width: 160, height: 25)
            nodes.append(ChessNativePlan.Node(index: i, role: item.0, label: item.1, identifier: item.2, selected: item.1 == "Главная", rect: frame))
        }
        return ChessNativePlan.Snapshot(nodes: nodes, window: CGRect(x: 0, y: 0, width: 900, height: 900), focused: focused)
    }
    static func main() throws {
        let home = snapshot([("AXGroup","","Chess.HomeScreenHosting"),("AXButton","три минуты",""),("AXButton","Начать партию","")])
        let playing = snapshot([("AXRadioButton","Главная",""),("AXStaticText","2:58",""),("AXStaticText","2:46",""),("AXGroup","Индикатор качества связи","")])
        let win = snapshot([("AXStaticText","Вы выиграли!",""),("AXButton","Новая партия","")])
        let plan = ChessNativePlan()
        func step(_ view: ChessNativePlan.Snapshot, _ now: Double, minutes: Int = 3, increment: Int = 0, enabled: Bool = true, allowed: Bool = true) -> ChessNativePlan.Decision {
            plan.step(view, enabled: enabled, allowed: allowed, minutes: minutes, increment: increment, delay: 3, revision: 0, now: now)
        }
        try check(step(home, 0).press == nil,"Connecting to the native home never starts the first game")
        try check(step(home, 1, minutes: 5).press == 1,"Changing the native clock opens only its time picker")
        let picker = snapshot([("AXHeading","Выбрать время",""),("AXStaticText","пять минут",""),("AXStaticText","Другие контроли времени","")])
        try check(step(picker,2,minutes:5).press == 1,"The exact native clock is selected using accessibility labels")
        try check(step(picker,3,minutes:5,increment:5).press == 2,"Additional controls open when an increment preset is not visible")
        _ = step(home,4);try check(step(playing,5).phase == "playing","Native live games are observed without touching any move")
        try check(step(win,10).seconds == 3,"A native result gets a three-second countdown")
        try check(step(win,12).press == nil,"The native countdown permits cancellation before submission")
        let submit = step(win,13)
        try check(submit.press == 1 && submit.submits,"One new-game button is pressed only with the verified clock")
        try check(step(win,14).press == nil,"Repeated native result snapshots never queue twice")
        try check(step(win,26).code == "start-unavailable","An unconfirmed native search pauses instead of retrying")
        _ = step(playing,30)
        let draw = snapshot([("AXStaticText","Ничья по соглашению",""),("AXButton","Новая партия","")])
        try check(step(draw,31).phase == "countdown","Draws are recognized even when the result modal hides the home tab")
        try check(step(draw,32,enabled:false).press == nil,"Switching off cancels a native result countdown")
        try check(step(win,35,allowed:false).press == nil,"A different leased board cannot submit a native game")
        try check(step(win,36,minutes:7,increment:12).code == "time-unavailable","Unsupported native custom clocks never silently use another time")
        let review = snapshot([("AXGroup","","GameReview.GameReviewLoadingViewHosting"),("AXButton","Новая партия",""),("AXStaticText","Вы выиграли!","")])
        try check(step(review,40).phase == "unsupported","Game Review never triggers automatic matchmaking")
        let paywall = snapshot([("AXStaticText","Вы выиграли!",""),("AXButton","Новая партия",""),("AXButton","Попробуй за 0,00 ₽","")])
        try check(step(paywall,45).press == nil,"A subscription offer blocks automation and is never accepted or dismissed")
        let background = ChessNativePlan.Snapshot(nodes: home.nodes,window:home.window,focused:false)
        try check(step(background,46).press == nil,"The native adapter never clicks while another application is in front")
        let clocks: [(String, Int, Int)] = [("один минута",1,0),("один минута плюс один секунда на ход",1,1),("два минуты плюс один секунда на ход",2,1),("три минуты плюс два секунды на ход",3,2),("пять минут плюс пять секунд на ход",5,5),("десять минут плюс пять секунд на ход",10,5),("пятнадцать минут плюс десять секунд на ход",15,10),("один час",60,0),("five minutes plus five seconds per move",5,5)]
        for (label,m,i) in clocks {
            let parsed=ChessNativePlan.clock(label);try check(parsed?.0==m && parsed?.1==i,"Native clock label: "+label)
        }
        try check(ChessNativePlan.clock("1:49")==nil,"A running game clock is never confused with a matchmaking time control")
        let result: [String:Any] = ["ok":true,"checks":checks.count,"details":checks,"liveGamesStarted":false,"nativePermissionGranted":false]
        let data=try JSONSerialization.data(withJSONObject:result,options:[.prettyPrinted,.sortedKeys]);try data.write(to:URL(fileURLWithPath:"artifacts/chess-auto-next/native-plan-checks.json"));print(String(data:data,encoding:.utf8)!)
    }
}
