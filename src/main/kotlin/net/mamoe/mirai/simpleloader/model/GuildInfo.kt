package model

/**
 * 公会状态使用基础类
 */
class GuildInfoData{
    var code:Long = 1
    var data:GuildInfo? = null
}
class GuildInfo{
    var battle_info:BattleInfo? = null
    var boss_info:BossInfo? = null
    var clan_info:ClainInfo? = null
    var day_list: List<String>? = null
}

class BattleInfo{
    var id:Long = 0L
    var name:String = ""
}

class BossInfo{
    var lap_num:Long = 0L
    var name:String = ""
    var current_life:Long = 0L
    var total_life:Long = 0L
}

class ClainInfo{
    var last_total_ranking:String = ""
    var name:String = ""
    var last_ranking:Long = 0L
}