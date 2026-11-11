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
# Storage permission (only used on Android 9 and older)
s = open(manifest, encoding='utf-8').read()
if 'WRITE_EXTERNAL_STORAGE' not in s:
    s = s.replace('<application', '<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />\n    <application', 1)
    open(manifest, 'w', encoding='utf-8').write(s)
# Put every Java file from native/ into the app, so the file-saving plugin can never be left out of the APK.
import glob, os
pkg = 'android/app/src/main/java/com/shelfie/app'
os.makedirs(pkg, exist_ok=True)
for f in sorted(glob.glob('native/*.java')):
    shutil.copy(f, os.path.join(pkg, os.path.basename(f)))
    print('copied', f)
for need in ('MainActivity.java', 'ShelfieFilesPlugin.java', 'ShelfieIconPlugin.java', 'ShelfieWebPlugin.java'):
    if not os.path.exists(os.path.join(pkg, need)):
        raise SystemExit('BUILD STOPPED: native/' + need + ' is missing from the repo, so backups could not work.')
if 'ShelfieFilesPlugin' not in open(os.path.join(pkg, 'MainActivity.java'), encoding='utf-8').read():
    raise SystemExit('BUILD STOPPED: MainActivity.java does not register ShelfieFilesPlugin.')
print('file-saving plugin is included')
# ---- Switchable app icons: one launcher alias + one adaptive icon per colour ----
ICONS = [("terracotta","#C2553A","#FAF0E4","#F0D3C4"),("forest","#3F7A4B","#F2F5EC","#CFE3C9"),
         ("midnight","#2A1A47","#EADFFF","#A86BF0"),("rose","#D1566B","#FFF1F2","#F7D0D6"),
         ("ocean","#2B7FB8","#EEF6FB","#C4DFEF"),("lavender","#7A5BD0","#F4EFFC","#D9CCF3"),
         ("mocha","#3A2A21","#F4E8DC","#D58B4C"),("cream","#F7EFE2","#B8651F","#E3B98A")]
BARS = [(150,181,170,0),(210,159,214,1),(270,175,183,0),(330,164,204,1)]
SC = 0.185
def _tx(x): return (x-256.5)*SC+54
def _ty(y): return (y-281.0)*SC+54
def _rr(x,y,w,h,r):
    X,Y,W,H,R = _tx(x),_ty(y),w*SC,h*SC,r*SC
    return ("M%.2f,%.2fh%.2fa%.2f,%.2f 0 0 1 %.2f,%.2fv%.2fa%.2f,%.2f 0 0 1 -%.2f,%.2fh-%.2fa%.2f,%.2f 0 0 1 -%.2f,-%.2fv-%.2fa%.2f,%.2f 0 0 1 %.2f,-%.2fz"
            % (X+R,Y,W-2*R,R,R,R,R,H-2*R,R,R,R,R,W-2*R,R,R,R,R,H-2*R,R,R,R,R))
res = 'android/app/src/main/res'
for d in ('drawable','mipmap-anydpi-v26','values'):
    os.makedirs(os.path.join(res,d), exist_ok=True)
colors = ['<?xml version="1.0" encoding="utf-8"?>\n<resources>']
for name,bg,c1,c2 in ICONS:
    colors.append('    <color name="shi_bg_%s">%s</color>' % (name,bg))
    paths = ''.join('    <path android:fillColor="%s" android:pathData="%s"/>\n' % (c2 if b[3] else c1, _rr(b[0],b[1],47,b[2],10)) for b in BARS)
    paths += '    <path android:fillColor="%s" android:pathData="%s"/>\n' % (c1, _rr(130,385,253,18,9))
    open(os.path.join(res,'drawable','shi_fg_%s.xml' % name),'w',encoding='utf-8').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n%s</vector>\n' % paths)
    open(os.path.join(res,'mipmap-anydpi-v26','shi_%s.xml' % name),'w',encoding='utf-8').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/shi_bg_%s"/>\n    <foreground android:drawable="@drawable/shi_fg_%s"/>\n</adaptive-icon>\n' % (name,name))
colors.append('</resources>\n')
open(os.path.join(res,'values','shi_colors.xml'),'w',encoding='utf-8').write('\n'.join(colors))
s = open(manifest, encoding='utf-8').read()
if 'Icon_terracotta' not in s:
    # the launcher entry moves from MainActivity to the aliases
    s = s.replace('<category android:name="android.intent.category.LAUNCHER" />', '', 1)
    aliases = ''
    for i,(name,bg,c1,c2) in enumerate(ICONS):
        aliases += ('\n        <activity-alias android:name=".Icon_%s" android:targetActivity=".MainActivity"'
                    ' android:enabled="%s" android:exported="true" android:icon="@mipmap/shi_%s" android:label="@string/app_name">'
                    '\n            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />'
                    '\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>'
                    '\n        </activity-alias>') % (name, 'true' if i == 0 else 'false', name)
    s = s.replace('</application>', aliases + '\n    </application>', 1)
    open(manifest, 'w', encoding='utf-8').write(s)
print('app icons added')
print('patched')
