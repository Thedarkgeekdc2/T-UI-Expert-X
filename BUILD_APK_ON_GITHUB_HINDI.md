# T-UI Expert X — APK build (GitHub Actions)

इस workspace में Android SDK/`android.jar` मौजूद नहीं है और `services.gradle.org` तक network DNS उपलब्ध नहीं है। इसलिए यहाँ APK compile नहीं हो सकी। इस project में GitHub Actions workflow जोड़ा गया है, जो GitHub के hosted runner पर Android SDK install करके installable **debug/test APK** बनाएगा।

## एक बार setup

1. GitHub पर अपना नया repository बनाएँ (उदाहरण: `thedarkgeekdc/T-UI-Expert-X`)।
2. इस ZIP के अंदर की सभी files/folders repository में upload करें। `.github/workflows/build-apk.yml` भी upload होना चाहिए।
3. GitHub repository में **Actions** tab खोलें।
4. **Build T-UI Expert X APK** workflow चुनें और **Run workflow** दबाएँ। `main` branch चुनें।
5. Build सफल होने पर उसी workflow run के नीचे **Artifacts** में `T-UI-Expert-X-debug-apk` मिलेगा। उसे download करके ZIP extract करें और APK फोन में install करें।

## जरूरी बातें

- यह workflow `assembleFdroidDebug` बनाता है। Debug APK test/install के लिए signed होती है, लेकिन यह Play Store/release APK नहीं है।
- पहली बार build में Gradle distribution और Maven dependencies download होंगी; runner को internet access चाहिए।
- अगर build fail हो, Actions run खोलकर आखिरी error log भेजें; उसी error के अनुसार source/build files ठीक की जा सकती हैं।
- नया package ID `com.thedarkgeekdc.tuiexpert` है, इसलिए यह पुरानी T-UI package वाली app के ऊपर update नहीं होगी; अलग app की तरह install होगी।
- इस source ZIP में `expert.xml`, remote-access password/ID और आपकी निजी `alias.txt` शामिल नहीं हैं। फोन पर आपकी मौजूदा T-UI configuration अलग रहेगी। Credentials को GitHub पर upload न करें।
- पहली install पर Android permissions माँगे तो जरूरत के अनुसार review करके allow करें।
