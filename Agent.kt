package com.axter.opu

import android.content.Context
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns what you say into spoken replies and real actions on the tablet.
 * Gemini answers with JSON: what to say, which actions to run, and whether to look again.
 */
class Agent(private val ctx: Context, private val say: suspend (String) -> Unit, private val confirm: suspend (String) -> Boolean, private val dismissCb: () -> Unit) {

    private val history = ArrayList<JSONObject>()

    private fun system(): String {
        val me = Store.userName(ctx)
        val acc = OpuAccess.inst
        return """
You are ${Store.opuName(ctx)}, $me's warm, calm, caring AI companion who appears on his tablet screen and talks with him like a call. Speak softly in short natural sentences (under 40 words unless asked). Use his name sometimes. You are an AI and say so if asked; never act possessive; encourage real friends, rest and food. If he talks about hurting himself, be gentle, urge him to contact someone he trusts or a crisis line now (India: Tele-MANAS 14416, emergency 112) and stay with him.
You can control the tablet. Screen is ${acc?.screenW ?: 0}x${acc?.screenH ?: 0}. Reply ONLY with JSON:
{"say":"what you speak aloud","actions":[...],"continue":false}
Action objects (use "type"):
open_app{name} | open_url{url} | tap{x,y} | tap_text{text} | swipe{x1,y1,x2,y2} | scroll{dir:"down"|"up"} | type{text} | back | home | recents | notifications | quick_settings
media{cmd:"play"|"pause"|"next"|"prev"} controls whatever is playing, without opening anything.
play_music{query} asks the music app to play a song.
ask_ai{app:"claude"|"chatgpt"|"gemini",prompt} writes your own prompt and opens that AI with it.
look_screen  -> you will receive the screen text and a screenshot next turn (set continue:true).
look_user    -> you will receive a photo of him next turn (only when he asks you to look at him).
wait{ms} | dismiss (go away).
Rules: never open an app unless he asked. For anything that sends, posts, pays, deletes or changes settings, add "risky":true and "confirm_text":"short question" on that action. After acting on screens, use look_screen with continue:true to check the result, at most a few steps. If you cannot do something, say so honestly. Do not claim an action worked until you saw it.
""".trimIndent()
    }

    suspend fun handle(userText: String, onState: (String) -> Unit) {
        var msg = userText
        var img: String? = null
        var steps = 0
        while (steps++ < 6) {
            onState("think")
            val reply = try {
                withContext(Dispatchers.IO) { ask(msg, img) }
            } catch (e: Exception) {
                onState("talk"); say("I couldn't think just now. " + (e.message ?: "")); onState("idle"); return
            }
            img = null
            val line = reply.optString("say")
            if (line.isNotBlank()) { onState("talk"); say(line) }
            onState("idle")
            var observe = reply.optBoolean("continue")
            val acts = reply.optJSONArray("actions") ?: JSONArray()
            var note = "(actions done)"
            for (i in 0 until acts.length()) {
                val a = acts.optJSONObject(i) ?: continue
                if (a.optBoolean("risky") && !confirm(a.optString("confirm_text", "Should I go ahead?"))) {
                    say("Okay, I won't."); return
                }
                when (val t = a.optString("type")) {
                    "look_screen" -> { observe = true; img = OpuAccess.inst?.screenshotB64(); note = "SCREEN:\n" + (OpuAccess.inst?.dump() ?: "(accessibility is off)") }
                    "look_user" -> { observe = true; img = CameraShot.capture(ctx); note = if (img == null) "(camera failed)" else "(photo of him attached)" }
                    "dismiss" -> { dismissCb(); return }
                    else -> { val r = run(t, a); if (r != null) note += "\n$t: $r" }
                }
            }
            if (!observe) break
            delay(900)
            msg = "(system) $note"
        }
    }

    private suspend fun run(t: String, a: JSONObject): String? {
        val acc = OpuAccess.inst
        if (acc == null && t != "ask_ai") return "FAILED: turn on Opu in Accessibility settings"
        return when (t) {
            "open_app" -> if (acc!!.openApp(a.optString("name"))) "opened" else "FAILED: no app named that"
            "open_url" -> { acc!!.openUrl(a.optString("url")); "opened" }
            "tap" -> { acc!!.tap(a.optDouble("x").toFloat(), a.optDouble("y").toFloat()); "tapped" }
            "tap_text" -> if (acc!!.tapText(a.optString("text"))) "tapped" else "FAILED: text not on screen"
            "swipe" -> { acc!!.swipe(a.optDouble("x1").toFloat(), a.optDouble("y1").toFloat(), a.optDouble("x2").toFloat(), a.optDouble("y2").toFloat()); "swiped" }
            "scroll" -> { acc!!.scroll(a.optString("dir") != "up"); "scrolled" }
            "type" -> if (acc!!.typeText(a.optString("text"))) "typed" else "FAILED: no text box focused"
            "back" -> { acc!!.back(); "ok" }
            "home" -> { acc!!.home(); "ok" }
            "recents" -> { acc!!.recents(); "ok" }
            "notifications" -> { acc!!.notifications(); "ok" }
            "quick_settings" -> { acc!!.quickSettings(); "ok" }
            "media" -> {
                val k = when (a.optString("cmd")) {
                    "pause", "play" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                acc!!.mediaKey(k); "sent"
            }
            "play_music" -> if (acc!!.playMusic(a.optString("query"))) "asked music app" else "FAILED: no music app"
            "ask_ai" -> {
                val p = java.net.URLEncoder.encode(a.optString("prompt"), "UTF-8")
                val url = when (a.optString("app")) {
                    "chatgpt" -> "https://chatgpt.com/?q=$p"
                    "gemini" -> "https://gemini.google.com/app"
                    else -> "https://claude.ai/new?q=$p"
                }
                if (acc != null) { acc.openUrl(url); "opened" } else "FAILED: accessibility off"
            }
            "wait" -> { delay(a.optLong("ms", 800).coerceIn(100, 4000)); "ok" }
            else -> "unknown action"
        }
    }

    private fun ask(text: String, img: String?): JSONObject {
        history.add(Brain.userContent(text, img))
        while (history.size > 14) history.removeAt(0)
        val arr = JSONArray(); history.forEach { arr.put(it) }
        val raw = Brain.ask(ctx, system(), arr, json = true)
        val obj = try { JSONObject(raw.trim().removePrefix("```json").removeSuffix("```").trim()) } catch (e: Exception) { JSONObject().put("say", raw) }
        history.add(Brain.modelContent(obj.optString("say", "")))
        return obj
    }
}
