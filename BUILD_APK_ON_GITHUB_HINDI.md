# T-UI Expert X — Updated APK बनाना

इस source refresh में नया APK इस workspace में compile नहीं किया गया है। यहाँ Android SDK/`android.jar` और Gradle 6.1.1 उपलब्ध नहीं हैं। इसलिए updated source को अपने मौजूदा GitHub repository `Thedarkgeekdc2/T-UI-Expert-X` में commit/push करें।

## Steps

1. इस ZIP की सभी files को अपने मौजूदा repository के project root में रखें।
2. पुरानी files को replace करते समय अपनी local `expert.xml` और `alias.txt` को ZIP/GitHub में upload न करें। ये फोन की निजी files हैं और source ZIP में शामिल नहीं हैं।
3. Updated files को commit और push करें।
4. GitHub repository में **Actions** tab खोलें।
5. **Build T-UI Expert X APK** workflow का run खोलें। `main` branch पर push होने पर workflow अपने आप शुरू होगा।
6. Build सफल होने पर workflow run के **Artifacts** section से `T-UI-Expert-X-debug-APK` download करें, ZIP extract करें और APK install करें।

## जरूरी जानकारी

- Workflow path: `.github/workflows/gradle.yml`
- Gradle task: `:app:assembleFdroidDebug`
- यह installable debug/test APK है, Play Store release APK नहीं।
- App ID: `com.thedarkgeekdc.tuiexpert`
- Version: `0.5.8` (versionCode `17`)
- पुरानी T-UI app अलग package ID से installed हो सकती है; नई app parallel install होगी।
- Remote server केवल trusted same-Wi-Fi subnet के लिए है। Link HTTP है, HTTPS नहीं; router port forwarding न करें।

Build fail हो तो Actions log की आखिरी error share करें। उसी source revision के हिसाब से fix किया जा सकता है।
