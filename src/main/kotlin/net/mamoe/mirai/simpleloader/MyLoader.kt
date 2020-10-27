package net.mamoe.mirai.simpleloader

import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.BossInfo
import model.GuildInfo
import model.GuildInfoData
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.MemberJoinEvent
import net.mamoe.mirai.event.events.MemberMuteEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.join
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.simpleloader.model.BossReportData
import net.mamoe.mirai.simpleloader.model.ReportBossInfo
import net.mamoe.mirai.utils.BotConfiguration
import okhttp3.CookieJar
import util.Http
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.fixedRateTimer

//=================Config============================
//Bot的QQ号
var qqId:Long = 382950487L
//Bot的密码
var password:String = ""
//bigfun登录cookies
var cookies = ""
//=================Config============================
var daoInfoUrl = "https://www.bigfun.cn/api/feweb?target=gzlj-clan-day-report%2Fa&page=1&size=30"
var guildInfoUrl = "https://www.bigfun.cn/api/feweb?target=gzlj-clan-day-report-collect%2Fa"
var bossReportUrl = "https://www.bigfun.cn/api/feweb?target=gzlj-clan-boss-report-collect%2Fa"
var listenerGroupId = 134290932L
var currentBossName: String? = null
var currentLife: Long? = 0L
var bossNameInfo:MutableList<String> = mutableListOf()
var bookBoss1:MutableList<Long> = mutableListOf()
var bookBoss2:MutableList<Long> = mutableListOf()
var bookBoss3:MutableList<Long> = mutableListOf()
var bookBoss4:MutableList<Long> = mutableListOf()
var bookBoss5:MutableList<Long> = mutableListOf()
var dateEnd:String? = ""
var dateStart:String? = ""

suspend fun main() {
    while (true) {
        var miraiBot = Bot(qqId, password){protocol = BotConfiguration.MiraiProtocol.ANDROID_PAD}.alsoLogin()
        miraiBot.subscribeMessages {
            "你好" reply "你好!"
            case("at me") {
                reply(At(sender as Member) + " 给爷爬 ")
            }
        }

        miraiBot.subscribeAlways<MemberJoinEvent> {
            it.group.sendMessage(PlainText("欢迎大佬 ${it.member.nameCardOrNick} 加入本群！"))
        }
        miraiBot.subscribeAlways<MemberMuteEvent> {
            it.group.sendMessage(PlainText("此时一位傻嗨 ${it.member.nameCardOrNick} 被禁言了"))
        }

        miraiBot.subscribeAlways<GroupMessageEvent> {
            val group = this.group
            //pcr群
            if (group.id == listenerGroupId) {
                val msg = this.message.get(PlainText)?.content?: ""
                if (msg.startsWith("#")) {
                    var order =msg
                    if (msg.startsWith("#查刀")) {
                        order = "#查刀"
                    } else if (msg.startsWith("#预约")) {
                        order = "#预约"
                    }
                    when(order) {
                        "#状态" -> it.group.sendMessage(PlainText(getGuildInfoStr()))
                        "#查刀" -> it.group.sendMessage(PlainText(getDAO(msg)))
                        "#预约" -> it.group.sendMessage(PlainText(bookBoss(this.sender.id,msg)))
                        "#BOSS信息" -> it.group.sendMessage(PlainText(bossNameInfo.toString()))
                        "#BOSS信息刷新" -> {initBossInfo();it.group.sendMessage(PlainText("刷新成功"))}
                        else -> {
                            it.group.sendMessage(PlainText("指令有误，翻乡下种番薯啦你"))
                        }
                    }
                }

            }
        }

        //初始化BOSS数据
        initBossInfo()
        //定时查询boss血量变化
        scheduleBoss(miraiBot)

        miraiBot.join() // 等待 Bot 离线, 避免主线程退出
        miraiBot.close()
    }

}

fun getDAO(msg:String):String {
    var url = daoInfoUrl
    if (msg.length>3) {
        var s = msg.substringAfter("#查刀")
        url = "$url&date=$s"
    }
    var response = Http.getResponse(url, cookies)
    var json = response.body!!.string()
    var list: GroupData? = Gson().fromJson(json, GroupData::class.java)
    val data = list?.data
    if (data != null && data.isNotEmpty()) {
        var currentDao = 0.0
        var nameList:MutableList<String> = mutableListOf()
        for(groupModel : GroupModel in data ){
            currentDao += groupModel.number
            if (groupModel.number < 3) {
                nameList.add(groupModel.name+" "+groupModel.number+" ")
            }
        }
        var outStr = "$currentDao /90 "
        if (nameList.size > 0) {
            outStr += "以下人员小于三刀 "
            for(index in nameList.indices ){
                if (index < 10) {
                    outStr += nameList[index]
                }
            }
        }
        return "当前出刀状况为 $outStr"
    }
    return "无数据"
}

fun getCookies():CookieJar{
    var cookieJar:CookieJar = CookieJar.NO_COOKIES
    return cookieJar
}

fun getGuildInfoStr():String{
    val guildInfo = getGuildInfoData() ?: return "无数据"
    val lap_num = guildInfo.boss_info?.lap_num
    val bossName = guildInfo.boss_info?.name
    val current_life = guildInfo.boss_info?.current_life
    val total_life = guildInfo.boss_info?.total_life
    dateEnd = guildInfo.day_list?.get(0)
    dateStart = guildInfo.day_list?.get(guildInfo.day_list!!.size-1)
    val lastRanking = guildInfo.clan_info?.last_ranking
    currentBossName = bossName
    currentLife = current_life

    return "当前 $bossName($lap_num 周目)  生命值 $current_life / $total_life " +
            "排名 $lastRanking 出刀记录截止日 $dateEnd"
}

fun getGuildInfoData(): GuildInfo? {
    val response = Http.getResponse(guildInfoUrl, cookies)
    val json = response.body!!.string()
    val guildInfoData:GuildInfoData
    try {
        guildInfoData = Gson().fromJson(json, GuildInfoData::class.java)
    } catch (e : Exception) {
        return null
    }
    return guildInfoData.data
}

class GroupModel{
    var name:String = ""
    var number:Double = 0.0
}

class GroupData{
    var code:String = ""
    lateinit var data:List<GroupModel>
}

suspend fun scheduleBoss(miraiBot:Bot){
    while (true) {
        delay(450000L)
        if (dateEnd!!.isEmpty()) {
            getGuildInfoData()
        }
        if (dateEnd!!.isNotEmpty()) {
            var endDate:LocalDate? = null
            var starrDate:LocalDate? = null
            try {
                endDate = LocalDate.parse(dateEnd, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                starrDate = LocalDate.parse(dateStart, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e:java.lang.Exception) {
                endDate = null
                starrDate = null
            }
            if (endDate !=null && LocalDate.now().isBefore(endDate.plusDays(1))  && LocalDate.now().isAfter(starrDate?.minusDays(1))) {
                println("=====================启动定时查询BOSS协程======================")
                val guildInfo = getGuildInfoData()
                if (guildInfo != null) {
                    val bossName = guildInfo?.boss_info?.name
                    val current_life = guildInfo?.boss_info?.current_life
                    //预约提醒
                    if (currentBossName != bossName) {
                        miraiBot.getGroup(listenerGroupId).sendMessage("@BOSS变更播报")
                        miraiBot.getGroup(listenerGroupId).sendMessage(getGuildInfoStr())
                        if (bossNameInfo.isNotEmpty()) {
                            for (index in bossNameInfo.indices) {
                                if (bossName == bossNameInfo[index]) {
                                    var atMenberId:List<Long> = mutableListOf()
                                    when (index) {
                                        0 -> {atMenberId = bookBoss1;bookBoss1=mutableListOf()}
                                        1 -> {atMenberId = bookBoss2;bookBoss2=mutableListOf()}
                                        2 -> {atMenberId = bookBoss3;bookBoss3=mutableListOf()}
                                        3 -> {atMenberId = bookBoss4;bookBoss4=mutableListOf()}
                                        4 -> {atMenberId = bookBoss5;bookBoss5=mutableListOf()}
                                    }
                                    if (atMenberId.isNotEmpty()) {
                                        for (id:Long in atMenberId) {
                                            var group = miraiBot.getGroup(listenerGroupId)
                                            if (group.contains(id)) {
                                                group.sendMessage(At(group[id]))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (currentLife != current_life) {
                        miraiBot.getGroup(listenerGroupId).sendMessage("@血量变更播报")
                        miraiBot.getGroup(listenerGroupId).sendMessage(getGuildInfoStr())
                    }
                    currentBossName = bossName
                    currentLife = current_life
                }
            }
        }
    }
}

fun initBossInfo(){
    println("=====================初始化BOSS信息======================")
    val response = Http.getResponse(bossReportUrl, cookies)
    val json = response.body!!.string()
    var bossReportData: BossReportData? = null
    try {
        bossReportData = Gson().fromJson(json, BossReportData::class.java)
    } catch (e : Exception) {
        print("BOSS信息初始化失败")
    }
    if (bossReportData != null) {
        var bossReportInfo = bossReportData.data
        var bossList = bossReportInfo?.boss_list
        if (bossList != null) {
            for (bossInfo: ReportBossInfo in bossList) {
                bossNameInfo.add(bossInfo.boss_name?:"无名")
            }
            println("BOSS信息初始化成功")
            println(bossNameInfo.toString())
        }

    }

    println("=====================初始化BOSS信息结束======================")
}

fun bookBoss(id:Long,msg:String):String{
    var s = msg.substringAfter("#预约")
    if (s.isNotBlank()) {
        when(s){
            "1" -> bookBoss1.add(id)
            "2" -> bookBoss2.add(id)
            "3" -> bookBoss3.add(id)
            "4" -> bookBoss4.add(id)
            "5" -> bookBoss5.add(id)
        }
        return "预约成功"
    }
    return "预约失败，检查指令格式#预约+BOSS序号"
}