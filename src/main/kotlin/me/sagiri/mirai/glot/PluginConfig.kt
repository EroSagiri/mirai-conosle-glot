package me.sagiri.mirai.glot

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

object PluginConfig : AutoSavePluginConfig("PluginConfig") {
    var token by value<String>("")
    val regex by value<String>("^#\$code/s*(.+)")
}