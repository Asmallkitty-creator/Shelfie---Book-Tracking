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

# Launcher icon choices: MainActivity stops being the launcher; one alias per colour scheme is.
s = open(manifest, encoding='utf-8').read()
if 'IconTerracotta' not in s:
    s = s.replace('<category android:name="android.intent.category.LAUNCHER" />', '', 1)
    names = ['terracotta', 'forest', 'midnight', 'rose', 'ocean', 'lavender', 'mocha', 'cream']
    al = ''
    for n in names:
        al += f'''
        <activity-alias
            android:name=".Icon{n.capitalize()}"
            android:targetActivity=".MainActivity"
            android:enabled="{'true' if n == 'terracotta' else 'false'}"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_{n}"
            android:roundIcon="@mipmap/ic_launcher_{n}"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>'''
    s = s.replace('</application>', al + '\n    </application>', 1)
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
