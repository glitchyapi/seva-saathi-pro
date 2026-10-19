package org.sevasaathi.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Signed, content-addressed UI updates. APK installation is a separate user-confirmed path. */
public final class UpdateManager {
    public interface Listener {void send(String type,JSONObject data);}
    private static final Object LOCK=new Object();
    private static final String RAW="https://raw.githubusercontent.com/glitchyapi/seva-saathi-pro/main/docs/updates/stable.json";
    private static final String PAGES="https://glitchyapi.github.io/seva-saathi-pro/updates/stable.json";
    private final Context context;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final File root;
    private final byte[] publicKey;
    private volatile JSONObject latest;
    public UpdateManager(Context context,Listener listener) throws Exception {
        this.context=context.getApplicationContext();this.listener=listener;
        prefs=context.getSharedPreferences("release-state",Context.MODE_PRIVATE);
        root=new File(context.getFilesDir(),"ui-bundles");root.mkdirs();
        try(InputStream in=context.getAssets().open("update-public.der")){publicKey=BackupCodec.readLimited(in,8192);}
        File cached=new File(root,"release-envelope.json");
        if(cached.exists())try(FileInputStream in=new FileInputStream(cached)){latest=ReleaseProtocol.verify(new String(BackupCodec.readLimited(in,ReleaseProtocol.MAX_MANIFEST),StandardCharsets.UTF_8),publicKey);}catch(Exception ignored){latest=null;}
    }
    public static void prepareLaunch(Context context){
        SharedPreferences p=context.getSharedPreferences("release-state",Context.MODE_PRIVATE);
        int active=p.getInt("activeUi",0),ready=p.getInt("readyUi",0),previous=p.getInt("previousUi",0);
        if(p.getBoolean("pendingHealth",false)){
            p.edit().putInt("blockedUi",active).putInt("activeUi",previous).putBoolean("pendingHealth",false).commit();active=previous;
        }
        if(active<=BuildConfig.UI_VERSION){p.edit().putInt("activeUi",0).putInt("previousUi",0).commit();active=0;}
        if(ready>Math.max(active,BuildConfig.UI_VERSION) && ready!=p.getInt("blockedUi",-1)){
            File folder=new File(context.getFilesDir(),"ui-bundles/bundle-"+ready);
            if(folder.isDirectory())p.edit().putInt("previousUi",active).putInt("activeUi",ready).putInt("readyUi",0).putBoolean("pendingHealth",true).commit();
        }
    }
    public void healthy(){prefs.edit().putBoolean("pendingHealth",false).apply();}
    public int uiVersion(){return Math.max(BuildConfig.UI_VERSION,prefs.getInt("activeUi",0));}
    public boolean activate(){
        synchronized(LOCK){int ready=prefs.getInt("readyUi",0);if(ready<=uiVersion() || !new File(root,"bundle-"+ready).isDirectory())return false;
            return prefs.edit().putInt("previousUi",prefs.getInt("activeUi",0)).putInt("activeUi",ready).putInt("readyUi",0).putBoolean("pendingHealth",true).commit();}
    }
    public InputStream asset(String path) throws Exception {
        ReleaseProtocol.safePath(path);
        int active=prefs.getInt("activeUi",0);
        if(active>BuildConfig.UI_VERSION){File folder=new File(root,"bundle-"+active);File f=new File(folder,path);if(!f.getCanonicalPath().startsWith(folder.getCanonicalPath()+File.separator))throw new Exception("Asset path");if(f.isFile())return new FileInputStream(f);}
        return context.getAssets().open("web/"+path);
    }
    public JSONObject info() {
        JSONObject out=new JSONObject();
        try{out.put("versionName",BuildConfig.VERSION_NAME);out.put("versionCode",BuildConfig.VERSION_CODE);out.put("uiVersion",uiVersion());out.put("readyUi",prefs.getInt("readyUi",0));out.put("lastChecked",prefs.getLong("lastChecked",0));out.put("verified",latest!=null);out.put("notifications",notificationsAllowed());out.put("latest",latest==null?JSONObject.NULL:latest);out.put("apkReady",apkFile().exists() && latest!=null && prefs.getInt("apkReadyCode",0)==latest.getJSONObject("app").getInt("versionCode"));out.put("busy",false);}catch(Exception ignored){}
        return out;
    }
    private void send(String type,JSONObject data){if(listener!=null)listener.send(type,data);}
    public void check(boolean autoDownload) throws Exception {
        synchronized(LOCK){
            byte[] bytes;
            try{bytes=fetch(RAW,ReleaseProtocol.MAX_MANIFEST);}catch(Exception primary){bytes=fetch(PAGES,ReleaseProtocol.MAX_MANIFEST);}
            String envelope=new String(bytes,StandardCharsets.UTF_8);JSONObject p=ReleaseProtocol.verify(envelope,publicKey);
            if(p.getLong("sequence")<prefs.getLong("highestSequence",0))throw new Exception("Older release rejected");
            // Reusing a sequence with different signed bytes is rejected as well.
            String digest=ReleaseProtocol.sha256(bytes);
            if(p.getLong("sequence")==prefs.getLong("highestSequence",0) && !prefs.getString("sequenceHash",digest).equals(digest))throw new Exception("Release sequence reused");
            atomic(new File(root,"release-envelope.json"),bytes);latest=p;
            prefs.edit().putLong("highestSequence",p.getLong("sequence")).putString("sequenceHash",digest).putLong("lastChecked",System.currentTimeMillis()).commit();
            notifyUpdate();send("checked",info());
            if(autoDownload && latest.getJSONObject("ui").getInt("version")>uiVersion() && latest.getJSONObject("ui").getInt("minNative")<=BuildConfig.VERSION_CODE)stageUi();
        }
    }
    public void stageUi() throws Exception {
        synchronized(LOCK){
            if(latest==null)throw new Exception("Check for a release first");
            JSONObject ui=latest.getJSONObject("ui");int version=ui.getInt("version");
            if(version<=uiVersion() || version==prefs.getInt("blockedUi",-1))return;
            if(ui.getInt("minNative")>BuildConfig.VERSION_CODE)throw new Exception("A native update is needed");
            if(prefs.getInt("readyUi",0)==version && new File(root,"bundle-"+version).isDirectory()){send("ui-ready",info());return;}
            File temp=new File(root,"stage-"+version);deleteTree(temp);temp.mkdirs();
            try {
                JSONArray fs=ui.getJSONArray("files");
                for(int i=0;i<fs.length();i++){
                    JSONObject f=fs.getJSONObject(i);String path=f.getString("path");ReleaseProtocol.safePath(path);
                    int length=f.getInt("bytes");String hash=f.getString("sha256");byte[] bytes=null;
                    try(InputStream existing=asset(path)){byte[] candidate=BackupCodec.readLimited(existing,10*1024*1024);if(candidate.length==length && hash.equals(ReleaseProtocol.sha256(candidate)))bytes=candidate;}catch(Exception ignored){}
                    if(bytes==null)bytes=fetchVerified(f);
                    if(bytes.length!=length || !hash.equals(ReleaseProtocol.sha256(bytes)))throw new Exception("Asset verification failed");
                    File destination=new File(temp,path);if(!destination.getCanonicalPath().startsWith(temp.getCanonicalPath()+File.separator))throw new Exception("Asset path");destination.getParentFile().mkdirs();atomic(destination,bytes);
                }
                File finalFolder=new File(root,"bundle-"+version);if(finalFolder.exists())deleteTree(finalFolder);
                if(!temp.renameTo(finalFolder))throw new Exception("Cannot activate staged directory");
                prefs.edit().putInt("readyUi",version).commit();send("ui-ready",info());cleanup();
            } catch(Exception error){deleteTree(temp);throw error;}
        }
    }
    private void cleanup(){
        int active=prefs.getInt("activeUi",0),previous=prefs.getInt("previousUi",0),ready=prefs.getInt("readyUi",0);
        File[] folders=root.listFiles();if(folders==null)return;
        for(File f:folders){if(!f.isDirectory() || !f.getName().startsWith("bundle-"))continue;try{int v=Integer.parseInt(f.getName().substring(7));if(v!=active && v!=previous && v!=ready)deleteTree(f);}catch(Exception ignored){}}
    }
    public File apkFile(){return new File(context.getCacheDir(),"updates/release.apk");}
    public File prepareApk() throws Exception {
        synchronized(LOCK){
            if(latest==null || latest.getJSONObject("app").getInt("versionCode")<=BuildConfig.VERSION_CODE)throw new Exception("No Android update");
            JSONObject app=latest.getJSONObject("app"),a=app.getJSONObject("apk");File target=apkFile();target.getParentFile().mkdirs();
            if(!target.exists() || target.length()!=a.getLong("bytes") || !hashFile(target).equals(a.getString("sha256"))){
                File part=new File(target.getParentFile(),"release.part");
                try{downloadVerified(a,part);verifyApk(part,app.getInt("versionCode"));if(target.exists() && !target.delete())throw new Exception("Cannot replace update");if(!part.renameTo(target))throw new Exception("Cannot stage APK");}catch(Exception e){part.delete();throw e;}
            }
            verifyApk(target,app.getInt("versionCode"));prefs.edit().putInt("apkReadyCode",app.getInt("versionCode")).commit();send("apk-ready",info());return target;
        }
    }
    private void verifyApk(File file,int expectedCode) throws Exception {
        PackageManager pm=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo current=pm.getPackageInfo(context.getPackageName(),flags),incoming=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(incoming==null || !context.getPackageName().equals(incoming.packageName))throw new Exception("APK package mismatch");
        long code=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode;
        if(code!=expectedCode || code<=BuildConfig.VERSION_CODE || incoming.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new Exception("APK version mismatch");
        android.content.pm.Signature[] a=Build.VERSION.SDK_INT>=28?current.signingInfo.getApkContentsSigners():current.signatures,b=Build.VERSION.SDK_INT>=28?incoming.signingInfo.getApkContentsSigners():incoming.signatures;
        if(a==null || b==null || a.length!=b.length)throw new Exception("APK signer mismatch");
        java.util.Set<String> set=new java.util.HashSet<>();for(android.content.pm.Signature s:a)set.add(s.toCharsString());for(android.content.pm.Signature s:b)if(!set.contains(s.toCharsString()))throw new Exception("APK signer mismatch");
    }
    private boolean notificationsAllowed(){return (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && context.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    private void notifyUpdate() throws Exception {
        boolean newApp=latest.getJSONObject("app").getInt("versionCode")>BuildConfig.VERSION_CODE,newUi=latest.getJSONObject("ui").getInt("version")>uiVersion();
        long seq=latest.getLong("sequence");if((!newApp && !newUi) || prefs.getLong("notifiedSequence",0)>=seq || !notificationsAllowed())return;
        NotificationManager nm=context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("updates","App updates",NotificationManager.IMPORTANCE_DEFAULT));
        boolean hindi=false;try{JSONObject state=new Vault(context).load();hindi=state!=null && "hi".equals(state.optString("language"));}catch(Exception ignored){}
        Intent intent=new Intent(context,MainActivity.class).putExtra("showUpdates",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,100,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,"updates").setSmallIcon(R.drawable.ic_stat).setContentTitle(hindi?"सेवा साथी का अपडेट उपलब्ध है":"A little something new for Seva Saathi").setContentText(hindi?"सत्यापित अपडेट देखने के लिए टैप करें।":"Tap to review your verified update.").setAutoCancel(true).setContentIntent(pending).build();
        nm.notify(101,notification);prefs.edit().putLong("notifiedSequence",seq).apply();
    }
    /** OnlyFiles returns a landing page. Resolve a fresh time-limited binary link without executing HTML. */
    private static String resolveOnlyFiles(String url) throws Exception {
        java.net.URI u=new java.net.URI(url);
        if(!"onlyfiles.com".equals(u.getHost()) || u.getPath().startsWith("/dl/"))return url;
        HttpsURLConnection c=connection(url);String html;
        try(InputStream in=c.getInputStream()){html=new String(BackupCodec.readLimited(in,128*1024),StandardCharsets.UTF_8);}finally{c.disconnect();}
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("<a[^>]*class=\"download\"[^>]*href=\"(https://onlyfiles\\.com/dl/[^\"]+)\"",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        if(!m.find())throw new Exception("OnlyFiles binary link unavailable");
        String direct=m.group(1).replace("&amp;","&");ReleaseProtocol.trustedUrl(direct);
        if(!"onlyfiles.com".equals(new java.net.URI(direct).getHost()))throw new Exception("Unexpected OnlyFiles redirect");
        return direct;
    }
    private static java.util.List<String> urls(JSONObject f) throws Exception {
        java.util.List<String> urls=new java.util.ArrayList<>();urls.add(f.getString("url"));JSONArray mirrors=f.optJSONArray("mirrors");if(mirrors!=null)for(int i=0;i<mirrors.length();i++)urls.add(mirrors.getString(i));return urls;
    }
    private static byte[] fetchVerified(JSONObject f) throws Exception {
        Exception last=new Exception("No verified download source");
        for(String url:urls(f)){try{byte[] data=fetch(url,f.getInt("bytes"));if(data.length!=f.getInt("bytes") || !ReleaseProtocol.sha256(data).equals(f.getString("sha256")))throw new Exception("Asset hash mismatch");return data;}catch(Exception e){last=e;}}
        throw last;
    }
    private static void downloadVerified(JSONObject f,File destination) throws Exception {
        Exception last=new Exception("No verified APK source");
        for(String url:urls(f)){try{download(url,destination,f.getLong("bytes"),f.getString("sha256"));return;}catch(Exception e){last=e;destination.delete();}}
        throw last;
    }
    private static HttpsURLConnection connection(String url) throws Exception {
        for(int redirects=0;redirects<6;redirects++){
            ReleaseProtocol.trustedUrl(url);HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent","SevaSaathi/"+BuildConfig.VERSION_NAME);c.setRequestProperty("Cache-Control","no-cache");
            int status=c.getResponseCode();if(status>=300 && status<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new Exception("Missing redirect");url=new URL(new URL(url),location).toString();continue;}
            if(status!=200){c.disconnect();throw new Exception("HTTP "+status);}return c;
        }throw new Exception("Too many redirects");
    }
    private static byte[] fetch(String url,int max) throws Exception {
        HttpsURLConnection c=connection(resolveOnlyFiles(url));try(InputStream in=c.getInputStream()){if(c.getContentLengthLong()>max)throw new Exception("Download size limit");return BackupCodec.readLimited(in,max);}finally{c.disconnect();}
    }
    private static String hashFile(File file) throws Exception {MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)d.update(buffer,0,n);}return ReleaseProtocol.hex(d.digest());}
    private static void download(String url,File dest,long expected,String hash) throws Exception {
        HttpsURLConnection c=connection(resolveOnlyFiles(url));long total=0;MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(dest)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){total+=n;if(total>expected)throw new Exception("APK size limit");out.write(buffer,0,n);d.update(buffer,0,n);}out.getFD().sync();if(total!=expected || !hash.equals(ReleaseProtocol.hex(d.digest())))throw new Exception("APK hash mismatch");}finally{c.disconnect();}
    }
    static void atomic(File target,byte[] bytes) throws Exception {AtomicFile a=new AtomicFile(target);FileOutputStream out=null;try{out=a.startWrite();out.write(bytes);a.finishWrite(out);}catch(Exception e){if(out!=null)a.failWrite(out);throw e;}}
    private static void deleteTree(File f){if(f.isDirectory()){File[] list=f.listFiles();if(list!=null)for(File child:list)deleteTree(child);}f.delete();}
}
