package net.mamoe.mirai.simpleloader.model

class BossReportData{
    var code:Long = 1
    var data: BossReportInfo? = null
}

class BossReportInfo{
    var name:String? = null
    var boss_list: List<ReportBossInfo>? = null
    var first_page: List<damageInfo>? = null
}

class ReportBossInfo {
    var id:Long? = null
    var boss_name:String? = null
}

class damageInfo {
    var damage:Long? = null
    var datetime:Long? = null
    var kill:Long? = null
    var lap_num:Long? = null
    var name:String? = null
    var reimburse:Long? = null
    var score:Long? = null
}