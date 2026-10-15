YtDownloader - KitKat (API 19), no AndroidX.
1. Open the folder in Android Studio (it will generate gradle-wrapper.jar / gradlew).
2. Build & run. Files are saved to the public Downloads folder.
Certificates: app/src/main/assets/cacert.pem = Mozilla CA bundle (certifi 2026.7.22), 121 roots incl. GTS Root R1/R3/R4.
To refresh: pip download certifi, extract cacert.pem, replace the asset file.
If YouTube stops answering: change CLIENT_NAME / CLIENT_VERSION in YouTube.java.
