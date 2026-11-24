OPU 2.0 — Android app (Opu on your whole screen)

WHAT IT DOES
- Say "Opu" any time: she appears on top of whatever app is open and talks with you like a call.
- Stays after you close the app (a small "Opu is listening" notification keeps her alive).
- Double-tap her, or say "go" / "bye", and she goes away. Drag her to move her. Tap her to make her stop talking.
- Controls the tablet by voice: opens apps (only when you ask), taps, scrolls, types, Back/Home/Recents,
  notifications, media play/pause/next without opening an app, reads and looks at your screen, writes prompts
  and sends them to Claude / ChatGPT / Gemini by opening them.
- "Look at me": one front-camera photo goes to Gemini (not saved), nothing else.
- Asks you out loud before anything that sends, posts, pays, deletes or changes settings.
- Gemini picks a working model by itself.

BUILD THE APK FREE, FROM YOUR TABLET (no PC)
1. Make a free GitHub account, create a new repo called opu-android.
2. Unzip this folder on the tablet. In the repo: Add file -> Upload files -> select EVERYTHING
   (including the hidden .github folder; if your file manager hides it, tell me and I will give a version without it).
3. Open the repo's Actions tab, run "Build Opu APK" (or it runs on upload).
4. When it turns green, open the run -> Artifacts -> Opu-APK, download, unzip, install app-debug.apk
   (allow "install unknown apps" for your browser when asked).
5. Open Opu and do the 4 steps on screen: permissions, overlay, Accessibility -> Opu -> ON, battery -> allow,
   then paste your Gemini key and press Start.
If the build fails: open the failed run, copy the red error text and send it to me.

HONEST LIMITS
- Not compiled or tested on a device yet. Expect to send me an error or two.
- Always-listening uses Android's speech recognizer. It may beep/restart, needs internet on most tablets, uses battery,
  and some makers (Xiaomi, Oppo, Samsung) kill background apps: set Opu to "no restrictions" in battery.
- Playing a SPECIFIC song without any app showing is up to the music app. Pause/play/next work silently;
  "play X" asks Spotify/YouTube Music, which may flash on screen.
- Banking and some apps block screenshots/taps by design; Opu can't see or control those.
- The 3D body must be a .glb/.vrm file you supply (Elaina and other existing characters are not included).
- Voice is Android's own voice (free). Pick a female voice in Android Settings -> Text-to-speech.
