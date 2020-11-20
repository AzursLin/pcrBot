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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.fixedRateTimer

//=================Config============================
//Bot的QQ号
var qqId:Long = 764541109L
//Bot的密码
var password:String = ""
//bigfun登录cookies
var cookies = "_csrf=PEzFbCQmnGlR8SOFXw55nVHc; UM_distinctid=174b4b145ef5cc-07a884135082c9-333769-1fa400-174b4b145f0d2e; sid=ig6txj12; DedeUserID=551085062; DedeUserID__ckMd5=813f4be6526ff5b9; SESSDATA=d088f2c8%2C1618302946%2C9d6ec*a1; bili_jct=5923adbaefc485a3f70b3d43fd560c5b; session-api=6c01u1g0s65lcte56bnd47thke; CNZZDATA1275376637=10395078-1600758669-https%253A%252F%252Fwww.baidu.com%252F%7C1603763664"
//=================Config============================
var orderArray = arrayOf("#状态","#查刀","#预约","#BOSS信息","#BOSS信息刷新","#帮忙预约","#当前预约列表")
var daoInfoUrl = "https://www.bigfun.cn/api/feweb?target=gzlj-clan-day-report%2Fa&page=1&size=30"
var guildInfoUrl = "https://www.bigfun.cn/api/feweb?target=gzlj-clan-day-report-collect%2Fa"
var bossReportUrl = "https://www.bigfun.cn/api/feweb?target=gzlj-clan-boss-report-collect%2Fa"
//要通知的群号 当前只会通知一个群
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
                    } else if (msg.startsWith("#帮忙预约")) {
                        order = "#帮忙预约"
                    }
                    when(order) {
                        "#指令" -> it.group.sendMessage(PlainText(printOrderArray()))
                        orderArray[0] -> {it.group.sendMessage(PlainText(getGuildInfoStr()))
                                           getGuildStatus(miraiBot)}
                        orderArray[1] -> it.group.sendMessage(PlainText(getDAO(msg)))
                        orderArray[2] -> it.group.sendMessage(PlainText(bookBoss(this.sender.id,msg)))
                        orderArray[3] -> it.group.sendMessage(PlainText(bossNameInfo.toString()))
                        orderArray[4] -> {initBossInfo();it.group.sendMessage(PlainText("刷新成功"))}
                        orderArray[5] -> it.group.sendMessage(PlainText(helpBookBoss(msg)))
                        orderArray[6] -> printBookBossInfo(miraiBot)
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
    var bossSeq = 0
    for (index in bossNameInfo.indices) {
        if (bossName == bossNameInfo[index]) {
            bossSeq = index+1
        }
    }
    return "当前$bossSeq 号 $bossName($lap_num 周目)  生命值 $current_life / $total_life " +
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
        println("=====================进入定时查询BOSS协程======================")
        if (dateEnd!!.isEmpty()) {
            val guildInfo = getGuildInfoData()
            if(guildInfo != null) {
                dateEnd = guildInfo.day_list?.get(0)
                dateStart = guildInfo.day_list?.get(guildInfo.day_list!!.size-1)
            }
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
            //公会战期内
            if (endDate !=null && LocalDate.now().isBefore(endDate.plusDays(1))  && LocalDate.now().isAfter(starrDate?.minusDays(1))) {
                //凌晨五至五点十分
                if (LocalDateTime.now().hour == 5 && LocalDateTime.now().minute < 9) {
                    miraiBot.getGroup(listenerGroupId).sendMessage("@五点状态播报")
                    miraiBot.getGroup(listenerGroupId).sendMessage(getGuildInfoStr())
                }
                println("=====================启动定时查询BOSS协程======================")
                val guildInfo = getGuildInfoData()
                if (guildInfo != null) {
                    val bossName = guildInfo.boss_info?.name
                    val current_life = guildInfo.boss_info?.current_life
                    //预约提醒
                    if (bossName != null) {
                        if (currentBossName != bossName) {
                            //名字变动
                            miraiBot.getGroup(listenerGroupId).sendMessage("@BOSS变更播报")
                            miraiBot.getGroup(listenerGroupId).sendMessage(getGuildInfoStr())
                            callBooker(bossName,miraiBot)
                        }else if (currentLife != current_life) {
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
            bossNameInfo = mutableListOf()
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
    var s = msg.substringAfter(orderArray[2])
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

fun printOrderArray():String{
    var str = ""
    orderArray.forEach { item ->  str+= "$item "}
    return str
}

/**
 * 名字有变动时触发预约提醒
 * true 名字有变动
 */
suspend fun callBooker(bossName:String,miraiBot:Bot):Boolean{
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
        return true
    }

    return false
}

/**
 * 检查是否要提醒预约
 */
suspend fun getGuildStatus(miraiBot:Bot){
    val guildInfo = getGuildInfoData()
    if (guildInfo != null) {
        val bossName = guildInfo.boss_info?.name
        val current_life = guildInfo.boss_info?.current_life
        if (bossName != null && bossName != currentBossName) {
            callBooker(bossName,miraiBot)
        }
        currentBossName = bossName
        currentLife = current_life
    }
}

/**
 * #帮忙预约
 */
fun helpBookBoss(msg:String):String{
    var errorFormatMsg = "指令格式有误 #帮助预约[BOSS序号][空格][QQ号，多个以逗号分隔]"
    var strs = msg.split(" ")
    if (strs.size != 2) {
        return errorFormatMsg
    }
    try {
        var bossNum = strs[0].substringAfter(orderArray[5]).toInt()
        var bookers = strs[1].split(",")
        for (index in bookers.indices) {
            bookBoss(bookers[index].toLong(),orderArray[2]+bossNum)
        }
    } catch (e:java.lang.Exception) {
        return errorFormatMsg
    }
    return "预约成功"
}

suspend fun printBookBossInfo(miraiBot:Bot){
    var msgSendFlag = false
    for (index in 1..4) {
        var realBossNum = index+1
        var msg = "预约BOSS$realBossNum "
        var bookBossList:MutableList<Long> =mutableListOf()
        when (index) {
            0 -> bookBossList = bookBoss1
            1 -> bookBossList = bookBoss2
            2 -> bookBossList = bookBoss3
            3 -> bookBossList = bookBoss4
            4 -> bookBossList = bookBoss5
        }
        for (bossNum in bookBossList.indices) {
            try {
                val name = miraiBot.getGroup(listenerGroupId).get(bookBossList[bossNum]).nameCard
                val bookerNum = bookBossList[bossNum]
                msg += "$name($bookerNum) "
            } catch (e:java.lang.Exception) {
                continue
            }
        }
        if (msg != "预约BOSS$realBossNum ") {
            miraiBot.getGroup(listenerGroupId).sendMessage(msg)
        } else {
            msgSendFlag = true
        }
    }
    if (msgSendFlag) {
        miraiBot.getGroup(listenerGroupId).sendMessage("暂无预约")
    }

}

