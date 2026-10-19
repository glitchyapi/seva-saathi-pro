package org.sevasaathi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The WebView has no network access. Only embedded or signature-verified local UI is served. */
public final class MainActivity extends Activity {
    private static final String ORIGIN="https://app.sevasaathi.invalid";
    private static final int BACKUP_SAVE=41,BACKUP_OPEN=42,BANNER_SAVE=43,NOTIFY_PERMISSION=44;
    private static final String CSP="default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; font-src 'self'; connect-src 'none'; media-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private WebView web;
    private Vault vault;
    private UpdateManager updates;
    private volatile boolean writable=false,pageReady=false,destroyed=false;
    private volatile byte[] pendingExport=null,pendingBanner=null;
    private volatile char[] importPassword=null;
    private volatile long lastCheckStarted=0;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(248,247,244));getWindow().setNavigationBarColor(Color.rgb(248,247,244));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        vault=new Vault(this);UpdateManager.prepareLaunch(this);
        try{updates=new UpdateManager(this,this::send);}catch(Exception e){new AlertDialog.Builder(this).setTitle("Seva Saathi").setMessage("The app package is incomplete. Please install the complete signed APK.").setPositiveButton("Close",(a,b)->finish()).show();return;}
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(248,247,244));
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);frame.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return insets;});}
        web=new WebView(this);web.setBackgroundColor(Color.rgb(248,247,244));web.setSaveEnabled(false);WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setDatabaseEnabled(false);settings.setAllowContentAccess(false);settings.setAllowFileAccess(false);settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);settings.setMediaPlaybackRequiresUserGesture(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setSafeBrowsingEnabled(true);
        web.addJavascriptInterface(new Bridge(),"SevaNative");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();
                if(!"https".equals(u.getScheme()) || !"app.sevasaathi.invalid".equals(u.getHost()) || !"GET".equals(request.getMethod()))return denied();
                String path=u.getPath();if(path==null || path.equals("/"))path="index.html";else if(path.startsWith("/"))path=path.substring(1);
                try {InputStream stream=updates.asset(path);Map<String,String> headers=new HashMap<>();headers.put("Content-Security-Policy",CSP);headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse(mime(path),"UTF-8",200,"OK",headers,stream);}catch(Exception e){return denied();}
            }
            @Override public void onPageFinished(WebView view,String url){pageReady=true;if(getIntent().getBooleanExtra("showUpdates",false)){getIntent().removeExtra("showUpdates");web.evaluateJavascript("document.querySelector('[data-page=space]').click(); setTimeout(function(){var b=document.querySelector('[data-action=updates]');if(b)b.click();},100);",null);}}
        });
        frame.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);web.loadUrl(ORIGIN+"/index.html");UpdateJob.schedule(this);
    }
    private static String mime(String p){if(p.endsWith(".html"))return "text/html";if(p.endsWith(".js"))return "application/javascript";if(p.endsWith(".css"))return "text/css";if(p.endsWith(".svg"))return "image/svg+xml";if(p.endsWith(".webp"))return "image/webp";if(p.endsWith(".woff2"))return "font/woff2";if(p.endsWith(".json"))return "application/json";if(p.endsWith(".png"))return "image/png";if(p.endsWith(".jpg"))return "image/jpeg";return "application/octet-stream";}
    private static WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",new HashMap<>(),new ByteArrayInputStream(new byte[0]));}
    private void send(String type,JSONObject data){runOnUiThread(()->{if(web!=null && !destroyed)web.evaluateJavascript("window.onNativeEvent&&window.onNativeEvent("+JSONObject.quote(type)+","+(data==null?"{}":data.toString())+");",null);});}
    private void error(String code){JSONObject data=new JSONObject();try{data.put("code",code);}catch(Exception ignored){}send("error",data);}
    private void check(boolean force){if(updates==null)return;if(!force && System.currentTimeMillis()-lastCheckStarted<60000)return;lastCheckStarted=System.currentTimeMillis();worker.execute(()->{try{JSONObject s=null;boolean auto=false;try{s=vault.load();auto=s==null||s.optBoolean("autoUpdate",true);}catch(Exception ignored){}updates.check(auto);}catch(Exception e){error("checkFailed");}});}
    public final class Bridge {
        @JavascriptInterface public String loadVault(){JSONObject out=new JSONObject();try{JSONObject data=vault.load();writable=true;out.put("ok",true);out.put("data",data==null?JSONObject.NULL:data);}catch(Exception e){writable=false;try{out.put("ok",false);}catch(Exception ignored){}}return out.toString();}
        @JavascriptInterface public String saveVault(String text){if(!writable)return "{\"ok\":false}";try{vault.save(text);return "{\"ok\":true}";}catch(Exception e){return "{\"ok\":false}";}}
        @JavascriptInterface public String getInfo(){return updates.info().toString();}
        @JavascriptInterface public void markHealthy(){updates.healthy();}
        @JavascriptInterface public void checkUpdates(){check(true);}
        @JavascriptInterface public void downloadUi(){worker.execute(()->{try{updates.stageUi();}catch(Exception e){error("checkFailed");}});}
        @JavascriptInterface public void applyUi(){runOnUiThread(()->{if(updates.activate()){pageReady=false;web.loadUrl(ORIGIN+"/index.html");}});}
        @JavascriptInterface public void requestNotifications(){runOnUiThread(()->{NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("updates","App updates",NotificationManager.IMPORTANCE_DEFAULT));if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY_PERMISSION);}else{Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}});}
        @JavascriptInterface public void exportBackup(String pass){if(pass==null || pass.length()<12 || pass.length()>512){error("passwordShort");return;}char[] pw=pass.toCharArray();worker.execute(()->{try{JSONObject data=vault.load();if(data==null)data=StateValidator.empty();pendingExport=BackupCodec.encrypt(data.toString(),pw).getBytes(StandardCharsets.UTF_8);runOnUiThread(()->createDocument(BACKUP_SAVE,"application/octet-stream","Seva-Saathi-"+new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date())+".seva"));}catch(Exception e){error("saveFailed");}finally{Arrays.fill(pw,'\0');}});}
        @JavascriptInterface public void importBackup(String pass){if(pass==null || pass.length()>512)return;if(importPassword!=null)Arrays.fill(importPassword,'\0');importPassword=pass.toCharArray();runOnUiThread(()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");try{startActivityForResult(intent,BACKUP_OPEN);}catch(Exception e){error("restoreError");}});}
        @JavascriptInterface public void saveBanner(String base64){worker.execute(()->{try{if(base64==null || base64.length()>8*1024*1024)throw new Exception("Banner too large");byte[] bytes=java.util.Base64.getDecoder().decode(base64);if(bytes.length<8 || bytes[0]!=(byte)0x89 || bytes[1]!='P' || bytes[2]!='N' || bytes[3]!='G')throw new Exception("PNG format");pendingBanner=bytes;runOnUiThread(()->createDocument(BANNER_SAVE,"image/png","Bhandara-invitation.png"));}catch(Exception e){error("saveFailed");}});}
        @JavascriptInterface public void shareText(String text){if(text==null || text.length()>5000)return;runOnUiThread(()->{Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text);try{startActivity(Intent.createChooser(send,"Share invitation"));}catch(Exception e){error("noSharing");}});}
        @JavascriptInterface public void installUpdate(){worker.execute(()->{try{updates.prepareApk();runOnUiThread(()->openInstaller());}catch(Exception e){error("checkFailed");}});}
        @JavascriptInterface public void leave(){runOnUiThread(()->finish());}
    }
    private void openInstaller(){
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("Install a Seva Saathi update").setMessage("Android needs your permission to install updates from this app. Enable Allow from this source, then return and tap Open Android installer. Your journal will stay on this device.").setPositiveButton("Open settings",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())))).setNegativeButton("Not now",null).show();return;
        }
        Uri uri=Uri.parse("content://"+getPackageName()+".updates/release.apk");Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(intent);}catch(Exception e){error("checkFailed");}
    }
    private void createDocument(int request,String mime,String name){Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name);try{startActivityForResult(intent,request);}catch(Exception e){pendingExport=null;pendingBanner=null;error("saveFailed");}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null){if(importPassword!=null){Arrays.fill(importPassword,'\0');importPassword=null;}pendingExport=null;pendingBanner=null;send("cancelled",new JSONObject());return;}
        Uri uri=data.getData();
        if(request==BACKUP_SAVE || request==BANNER_SAVE){byte[] bytes=request==BACKUP_SAVE?pendingExport:pendingBanner;pendingExport=null;pendingBanner=null;worker.execute(()->{try{if(bytes==null)throw new Exception("Missing export");try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception("Cannot open document");out.write(bytes);out.flush();}send(request==BACKUP_SAVE?"backup-saved":"banner-saved",new JSONObject());}catch(Exception e){error("saveFailed");}finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}});}
        else if(request==BACKUP_OPEN){char[] pw=importPassword;importPassword=null;worker.execute(()->{try{if(pw==null)throw new Exception("Password lost; try again");String encrypted;try(InputStream in=getContentResolver().openInputStream(uri)){encrypted=new String(BackupCodec.readLimited(in,4*1024*1024),StandardCharsets.UTF_8);}String json=BackupCodec.decrypt(encrypted,pw);vault.restore(json);writable=true;send("restored",new JSONObject(json));}catch(Exception e){error("restoreError");}finally{if(pw!=null)Arrays.fill(pw,'\0');}});}
    }
    @Override public void onRequestPermissionsResult(int r,String[] permissions,int[] grants){super.onRequestPermissionsResult(r,permissions,grants);if(updates!=null)send("info",updates.info());}
    @Override protected void onResume(){super.onResume();if(pageReady && updates!=null){send("info",updates.info());check(false);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(pageReady && intent.getBooleanExtra("showUpdates",false)){web.evaluateJavascript("document.querySelector('[data-page=space]').click();setTimeout(function(){var b=document.querySelector('[data-action=updates]');if(b)b.click();},100);",null);intent.removeExtra("showUpdates");}}
    @Override public void onBackPressed(){if(web!=null && pageReady)web.evaluateJavascript("window.handleBack&&window.handleBack();",null);else super.onBackPressed();}
    @Override protected void onDestroy(){destroyed=true;worker.shutdown();if(importPassword!=null)Arrays.fill(importPassword,'\0');pendingExport=null;pendingBanner=null;if(web!=null){web.removeJavascriptInterface("SevaNative");web.destroy();web=null;}super.onDestroy();}
}
