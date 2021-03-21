package me.sagiri.mirai.glot

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import com.github.kevinsawicki.http.HttpRequest
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.content
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpRetryException
import java.net.SocketTimeoutException
import java.util.regex.Pattern

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "me.sagiri.mirai.glot",
        name = "mirai-console-plugin-glot",
        version = "0.1.0"
    ) {
        author("sagiri")

        info(
            """
            glot api
        """.trimIndent()
        )

        // author 和 info 可以删除.
    }
) {
    override fun onEnable() {
        logger.info { "Plugin loaded" }

        PluginConfig.reload()

        if (PluginConfig.token.isEmpty()) logger.warning("没有设置token")

        val token = PluginConfig.token
        var url = "https://glot.io/api/run"

        val getLangReq = HttpRequest.get(url)

        getLangReq.trustAllHosts()
        getLangReq.trustAllCerts()

        getLangReq.connectTimeout(60000)
        getLangReq.readTimeout(60000)

        var getLangReqStatusCode = 0
        try {
            getLangReqStatusCode = getLangReq.code()
        } catch (e: SocketTimeoutException) {
            logger.error("请求获取编程语言列表时失败")
            return
        }

        // 编程语言列表JSONArray
        val listLang = JSONArray(getLangReq.body())

        if (getLangReqStatusCode == 200) {
            globalEventChannel().subscribeAlways<MessageEvent> {

//                if (sender.id != 2476255563) return@subscribeAlways

                if (Pattern.compile("^#list").matcher(message.content).find()) {
                    var msg = ""
                    listLang.forEach { lang ->
                        if (lang is JSONObject) {
                            msg += "${lang.getString("name")} "
                        }
                    }

                    subject.sendMessage(msg)
                }

                listLang.forEach { lang ->
                    if (lang is JSONObject) {
                        val langUrl = lang.getString("url").toString() + "/latest"
                        val langName = lang.getString("name").toString()

                        val p = Pattern.compile(PluginConfig.regex.replace("\$code", langName)).matcher(message.content)
                        if (p.find()) {
                            if (p.groupCount() > 0) {
                                val code = p.group(1)

                                val runCodeReq = HttpRequest.post(langUrl)

                                runCodeReq.trustAllHosts()
                                runCodeReq.trustAllCerts()

                                runCodeReq.connectTimeout(60000)
                                runCodeReq.readTimeout(60000)

                                var runCodeReqStatusCode = 0

                                runCodeReq.header("Authorization", "Token $token")
                                runCodeReq.header("Content-type", "application/json")

                                val codeObject = RequestCode(
                                    listOf(
                                        CodeFile(
                                            langName,
                                            code
                                        )
                                    )
                                )
                                runCodeReq.send(JSONObject(codeObject).toString())

                                try {
                                    runCodeReqStatusCode = runCodeReq.code()
                                } catch (e: SocketTimeoutException) {
                                    subject.sendMessage("请求 $langUrl 超时")
                                }

                                if (runCodeReqStatusCode == 200) {
                                    val responseObject = JSONObject(runCodeReq.body())
                                    val stdout = responseObject.getString("stdout")
                                    val stderr = responseObject.getString("stderr")
                                    val error = responseObject.getString("error")

                                    if (stdout is String && stdout.isNotEmpty()) {
                                        subject.sendMessage(stdout)
                                    }

                                    if (stderr is String && stderr.isNotEmpty()) {
                                        subject.sendMessage(stderr)
                                    }

                                    if (error is String && error.isNotEmpty()) {
                                        subject.sendMessage("Error: $error")
                                    }

                                    return@subscribeAlways
                                } else {
                                    subject.sendMessage("请求 $langUrl 状态码是 $runCodeReqStatusCode")
                                }
                            }

                            return@subscribeAlways
                        } else {
                            return@forEach
                        }
                    } else {
                        return@forEach
                    }
                }
            }
        } else {
            logger.error("请求语言列表是返回状态码: $getLangReqStatusCode")
        }
    }
}

/**
 * 请求文件对象
 */
data class RequestCode(
    val files: List<CodeFile>
)

/**
 * 代码文件
 */
data class CodeFile(
    val name: String,
    val content: String
)