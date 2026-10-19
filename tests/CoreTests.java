import org.sevasaathi.app.BackupCodec;
import org.sevasaathi.app.StateValidator;
import org.sevasaathi.app.ReleaseProtocol;
import org.json.JSONObject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class CoreTests {
 static int tests=0;
 static void ok(boolean result,String name){if(!result)throw new AssertionError(name);tests++;System.out.println("PASS "+name);}
 static void rejects(RunnableChecked action,String name)throws Exception{boolean rejected=false;try{action.run();}catch(Exception e){rejected=true;}ok(rejected,name);}
 interface RunnableChecked{void run()throws Exception;}
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]);String plain="{\"schema\":1,\"language\":\"hi\",\"profile\":{\"name\":\"साथी\"},\"goal\":120,\"autoUpdate\":true,\"favorite\":false,\"entries\":[{\"id\":\"test-1\",\"title\":\"भंडारे की तैयारी\",\"date\":\"2026-08-01\",\"minutes\":45,\"category\":\"bhandara\",\"note\":\"सेवा का एक शांत दिन\",\"createdAt\":1}]}";
  char[] pw="a-long-test-password-2026".toCharArray();
  String backup=BackupCodec.encrypt(plain,pw),second=BackupCodec.encrypt(plain,pw);
  ok(!backup.equals(second),"fresh backup salt and nonce");
  ok(BackupCodec.decrypt(backup,pw).equals(plain),"encrypted compressed Hindi round trip");
  rejects(()->BackupCodec.decrypt(backup,"wrong-password-123456".toCharArray()),"wrong password rejected");
  JSONObject tampered=new JSONObject(backup);byte[] cipher=Base64.getDecoder().decode(tampered.getString("ciphertext"));cipher[3]^=1;tampered.put("ciphertext",Base64.getEncoder().encodeToString(cipher));
  rejects(()->BackupCodec.decrypt(tampered.toString(),pw),"tampered backup rejected");
  JSONObject iterations=new JSONObject(backup);iterations.put("iterations",Integer.MAX_VALUE);
  rejects(()->BackupCodec.decrypt(iterations.toString(),pw),"unbounded KDF work rejected");
  rejects(()->BackupCodec.encrypt(plain,"short".toCharArray()),"short backup password rejected");
  rejects(()->StateValidator.parse(plain.replace("2026-08-01","2026-02-30")),"invalid calendar date rejected");
  rejects(()->StateValidator.parse(plain.replace("\"minutes\":45","\"minutes\":0")),"invalid minutes rejected");
  rejects(()->ReleaseProtocol.safePath("../../journal.vault"),"path traversal rejected");
  rejects(()->ReleaseProtocol.trustedUrl("http://raw.githubusercontent.com/a"),"cleartext download rejected");
  rejects(()->ReleaseProtocol.trustedUrl("https://github.com.evil.example/a"),"provider suffix attack rejected");
  ReleaseProtocol.trustedUrl("https://onlyfiles.com/example/app.js");ok(true,"OnlyFiles host explicitly allowed");
  rejects(()->ReleaseProtocol.safePath("evil.dex"),"native code cannot be delivered as interface assets");
  byte[] publicKey=Files.readAllBytes(root.resolve("app/src/main/assets/update-public.der"));String envelope=new String(Files.readAllBytes(root.resolve("docs/updates/stable.json")),StandardCharsets.UTF_8);
  JSONObject release=ReleaseProtocol.verify(envelope,publicKey);ok(release.getInt("sequence")>=1,"Python-signed release verified by Java");
  JSONObject changed=new JSONObject(envelope);byte[] payload=Base64.getDecoder().decode(changed.getString("payload"));payload[10]^=1;changed.put("payload",Base64.getEncoder().encodeToString(payload));
  rejects(()->ReleaseProtocol.verify(changed.toString(),publicKey),"tampered release signature rejected");
  Files.write(Paths.get(args[1]),backup.getBytes(StandardCharsets.UTF_8));
  if(args.length>2){String browser=new String(Files.readAllBytes(Paths.get(args[2])),StandardCharsets.UTF_8);ok(BackupCodec.decrypt(browser,pw).equals(plain),"browser backup imported by native Java codec");}
  System.out.println(tests+" core tests passed.");
 }
}
