"""Run by GitHub Actions after `npx cap add android`: wires in the timer."""
manifest = 'android/app/src/main/AndroidManifest.xml'
s = open(manifest, encoding='utf-8').read()
if 'TimerReceiver' not in s:
    s = s.replace('<application',
                  '<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />\n    <application', 1)
    s = s.replace('</application>', '''
        <receiver android:name=".TimerReceiver" android:exported="false" />
        <service
            android:name=".TimerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="reading_timer_notification" />
        </service>
        <activity
            android:name=".StopDialogActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Translucent.NoTitleBar"
            android:taskAffinity=""
            android:excludeFromRecents="true"
            android:launchMode="singleInstance" />
        <activity
            android:name=".DiscardDialogActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Translucent.NoTitleBar"
            android:taskAffinity=""
            android:excludeFromRecents="true"
            android:launchMode="singleInstance" />
    </application>''', 1)
    open(manifest, 'w', encoding='utf-8').write(s)

# Keep the same signing key on every build so new APKs install over old ones.
gradle = 'android/app/build.gradle'
g = open(gradle, encoding='utf-8').read()
if 'shelfie-debug.keystore' not in g:
    g += '''

android {
    signingConfigs {
        debug {
            storeFile file("${rootProject.projectDir}/../native/shelfie-debug.keystore")
            storePassword "android"
            keyAlias "androiddebugkey"
            keyPassword "android"
        }
    }
}
'''
    open(gradle, 'w', encoding='utf-8').write(g)
# App icon: copy our own launcher icons over the defaults.
import shutil
shutil.copytree('native/res', 'android/app/src/main/res', dirs_exist_ok=True)
print('patched')
