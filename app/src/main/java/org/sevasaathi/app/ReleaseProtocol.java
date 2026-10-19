package org.sevasaathi.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/** Release signatures bind the exact UTF-8 payload bytes, not a re-serialized JSON object. */
public final class ReleaseProtocol {
    public static final int MAX_MANIFEST=1024*1024;
    public static JSONObject verify(String envelope,byte[] publicDer) throws Exception {
        if(envelope.length()>MAX_MANIFEST)throw new Exception("Manifest too large");
        JSONObject wrapper=new JSONObject(envelope);
        if(!"SHA256withRSA".equals(wrapper.getString("algorithm")))throw new Exception("Signature algorithm");
        byte[] payload=Base64.getDecoder().decode(wrapper.getString("payload"));
        PublicKey publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicDer));
        Signature check=Signature.getInstance("SHA256withRSA");check.initVerify(publicKey);check.update(payload);
        if(!check.verify(Base64.getDecoder().decode(wrapper.getString("signature"))))throw new Exception("Signature rejected");
        JSONObject p=new JSONObject(new String(payload,StandardCharsets.UTF_8));validate(p);return p;
    }
    public static void validate(JSONObject p) throws Exception {
        if(p.getInt("schema")!=1 || !"stable".equals(p.getString("channel")) || p.getLong("sequence")<1)throw new Exception("Release format");
        JSONObject app=p.getJSONObject("app"),ui=p.getJSONObject("ui"),apk=app.getJSONObject("apk");
        if(app.getInt("versionCode")<1 || app.getInt("minVersionCode")<1 || app.getInt("minVersionCode")>app.getInt("versionCode") || !app.getString("versionName").matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.]+)?"))throw new Exception("App version");
        if(ui.getInt("version")<1 || ui.getInt("minNative")<1)throw new Exception("UI version");
        validateFile(apk,100*1024*1024);
        JSONArray files=ui.getJSONArray("files");
        if(files.length()<1 || files.length()>256)throw new Exception("File count");
        Set<String> names=new HashSet<>();long size=0;
        for(int i=0;i<files.length();i++) {
            JSONObject f=files.getJSONObject(i);String path=f.getString("path");
            safePath(path);if(!names.add(path))throw new Exception("Duplicate path");
            validateFile(f,10*1024*1024);size+=f.getLong("bytes");
        }
        if(size>25*1024*1024 || !names.contains("index.html"))throw new Exception("Bundle structure");
        JSONArray events=p.getJSONArray("events");if(events.length()>100)throw new Exception("Event limit");
        for(int i=0;i<events.length();i++){
            JSONObject e=events.getJSONObject(i);
            if(e.getString("title").trim().isEmpty() || e.getString("title").length()>120 || e.getString("venue").length()>200 || e.optString("description").length()>2000 || e.optString("titleHi").length()>120 || e.optString("descriptionHi").length()>2000)throw new Exception("Event fields");
            String date=e.getString("date");if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new Exception("Event date");
        }
        JSONObject notes=p.getJSONObject("notes");for(String lang:new String[]{"en","hi"}){JSONArray ns=notes.getJSONArray(lang);if(ns.length()>20)throw new Exception("Notes limit");for(int i=0;i<ns.length();i++)if(ns.getString(i).length()>500)throw new Exception("Note length");}
    }
    private static void validateFile(JSONObject f,long max) throws Exception {
        if(!f.getString("sha256").matches("[a-f0-9]{64}") || f.getLong("bytes")<1 || f.getLong("bytes")>max)throw new Exception("File metadata");
        trustedUrl(f.getString("url"));
        JSONArray mirrors=f.optJSONArray("mirrors");if(mirrors!=null){if(mirrors.length()>3)throw new Exception("Mirror limit");for(int i=0;i<mirrors.length();i++)trustedUrl(mirrors.getString(i));}
    }
    public static void safePath(String path) throws Exception {
        if(path.length()>180 || !path.matches("[a-zA-Z0-9][a-zA-Z0-9_./-]*") || path.contains("..") || path.contains("//") || path.endsWith("/"))throw new Exception("Unsafe asset path");
        if(!(path.endsWith(".html") || path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".json") || path.endsWith(".webp") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".svg") || path.endsWith(".woff2")))throw new Exception("Unsupported asset type");
    }
    public static void trustedUrl(String url) throws Exception {
        URI u=new URI(url);String host=u.getHost();
        if(!"https".equals(u.getScheme()) || host==null || u.getUserInfo()!=null || (u.getPort()!=-1 && u.getPort()!=443))throw new Exception("HTTPS required");
        // Pin provider domains as well as the release signing key. Add a verified mirror in a native release.
        if(!(host.equals("onlyfiles.com") || host.equals("raw.githubusercontent.com") || host.equals("github.com") || host.endsWith(".githubusercontent.com") || host.equals("glitchyapi.github.io")))throw new Exception("Unapproved download provider");
    }
    public static String sha256(byte[] bytes) throws Exception {return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    public static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private ReleaseProtocol(){}
}
