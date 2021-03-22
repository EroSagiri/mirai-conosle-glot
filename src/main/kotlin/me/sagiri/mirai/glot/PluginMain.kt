package me.sagiri.mirai.glot

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info
import com.github.kevinsawicki.http.HttpRequest
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
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
        val langDefaultFileName = mapOf<String,String>(
            "assembly" to "main.asm",
            "ats" to "main.dats",
            "bash" to "main.sh",
            "c" to "main.c",
            "clojure" to "main.clj",
            "cobol" to "main.cob",
            "coffeescript" to "main.coffee",
            "cpp" to "main.cpp",
            "crystal" to "main.cr",
            "csharp" to "main.cs",
            "d" to "main.d",
            "elixir" to "main.ex",
            "elm" to "Main.elm",
            "erlang" to "main.erl",
            "fsharp" to "main.fs",
            "go" to "main.go",
            "groovy" to "main.groovy",
            "haskell" to "main.hs",
            "idris" to "main.idr",
            "java" to "Main.java",
            "javascript" to "main.js",
            "julia" to "main.jl",
            "kotlin" to "main.kt",
            "lua" to "main.lua",
            "mercury" to "main.m",
            "nim" to "main.nix",
            "ocaml" to "main.ml",
            "perl" to "main.pl",
            "php" to "main.php",
            "python" to "main.py",
            "raku" to "main.raku",
            "swift" to "main.swift",
            "typescript" to "main.ts",
            "plaintext" to "main.txt",
            "nix" to "main.nix",
            "ruby" to "main.rb",
            "rust" to "main.rs",
            "scala" to "scala"
        )
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
                        var fileName : String? = null
                        if (lang is JSONObject) {
                            try{
                                fileName = langDefaultFileName.getValue(lang.getString("name").toString())
                            } catch (e: NoSuchElementException) {
                                logger.error(lang.getString("name").toString())
                            }
                            msg += "${lang.getString("name")} $fileName\n"
                        }
                    }

                    subject.sendMessage(msg)
                }

                if (Pattern.compile("^#help").matcher(message.content).find()) {
                    var msg = """
                        #list 获取支持编程语言列表
                        #help 获取帮助
                        #about 获取这个插件的信息
                    """.trimIndent()

                    subject.sendMessage(msg)
                }

                if (Pattern.compile("^#about").matcher(message.content).find()) {
                    var msg = """
                        mirai-console-glot
                        github: https://github.com/EromangaMe/mirai-conosle-glot
                        求打Star[mirai:face:111]
                        这是一个mirai-console的插件
                        使用glot的API
                        https://glot.io/
                    """.trimIndent()

                    subject.sendMessage(msg.deserializeMiraiCode())
                }

                listLang.forEach { lang ->
                    if (lang is JSONObject) {
                        val langUrl = lang.getString("url").toString() + "/latest"
                        val langName = lang.getString("name").toString()

                        val p = Pattern.compile(PluginConfig.regex.replace("\$land", langName), Pattern.DOTALL).matcher(message.content)
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

                                val fileName = langDefaultFileName.getValue(langName)
                                if(fileName !is String) {
                                    subject.sendMessage("默认文件名没有")
                                    return@subscribeAlways
                                }

                                val codeObject = RequestCode(
                                    listOf(
                                        CodeFile(
                                            fileName,
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
                                        subject.sendMessage(stdout.deserializeMiraiCode())
                                    }

                                    if (stderr is String && stderr.isNotEmpty()) {
                                        subject.sendMessage(stderr.deserializeMiraiCode())
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
