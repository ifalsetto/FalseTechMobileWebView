# FalseTech Mobile WebView APK Starter

This project turns `https://falsetechresell.com` into a basic Android APK using a WebView.

## Mobile-only build SOP

1. On your phone, open GitHub.
2. Create a new repository named:
   `FalseTech-Mobile-WebView`
3. Upload every file/folder from this ZIP into that repo.
4. Open the repo's **Actions** tab.
5. Tap **Build FalseTech Android APK**.
6. Tap **Run workflow**.
7. When it finishes, open the completed run.
8. Download the artifact named:
   `FalseTech-debug-apk`
9. Extract it on your phone.
10. Install `app-debug.apk`.

## Current app settings

- App Name: FalseTech
- Package: com.falsetech.mobile
- Start URL: https://falsetechresell.com
- Build type: Android WebView debug APK

## Edit the website URL

Open:
`app/src/main/java/com/falsetech/mobile/MainActivity.java`

Change:
`private static final String START_URL = "https://falsetechresell.com";`

## Notes

- Debug APKs install fine for personal testing.
- For Google Play release, generate a signed release APK/AAB later.
- Only publish apps for websites you own or have permission to wrap.
