package org.sevasaathi.app;

import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Portable authenticated backups. Compression precedes encryption. No cloud recovery key. */
public final class BackupCodec {
    public static final int ITERATIONS=600000;
    private static final byte[] AAD="seva-saathi-backup-v1".getBytes(StandardCharsets.UTF_8);
    public static byte[] readLimited(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
        while((count=in.read(buffer))!=-1) { if(out.size()+count>max) throw new Exception("File exceeds size limit"); out.write(buffer,0,count); }
        return out.toByteArray();
    }
    public static byte[] gzip(byte[] bytes) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(GZIPOutputStream zip=new GZIPOutputStream(out)) {zip.write(bytes);}
        return out.toByteArray();
    }
    public static byte[] gunzip(byte[] bytes) throws Exception {
        try(GZIPInputStream in=new GZIPInputStream(new ByteArrayInputStream(bytes))) {return readLimited(in,StateValidator.MAX_BYTES);}
    }
    private static SecretKeySpec derive(char[] password,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        byte[] key=null;
        try {key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();return new SecretKeySpec(key,"AES");}
        finally {spec.clearPassword();if(key!=null)Arrays.fill(key,(byte)0);}
    }
    public static String encrypt(String json,char[] password) throws Exception {
        StateValidator.parse(json);
        if(password.length<12 || password.length>512) throw new Exception("Password must be 12–512 characters");
        byte[] salt=new byte[16], iv=new byte[12]; SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,derive(password,salt),new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);
        byte[] zipped=gzip(json.getBytes(StandardCharsets.UTF_8));
        try {
            JSONObject out=new JSONObject();out.put("format","seva-saathi-backup");out.put("version",1);out.put("iterations",ITERATIONS);out.put("compression","gzip");
            out.put("salt",Base64.getEncoder().encodeToString(salt));out.put("iv",Base64.getEncoder().encodeToString(iv));out.put("ciphertext",Base64.getEncoder().encodeToString(cipher.doFinal(zipped)));return out.toString();
        } finally {Arrays.fill(zipped,(byte)0);}
    }
    public static String decrypt(String envelope,char[] password) throws Exception {
        if(envelope.length()>4*1024*1024 || password.length>512) throw new Exception("Size limit");
        JSONObject in=new JSONObject(envelope);
        if(!"seva-saathi-backup".equals(in.getString("format")) || in.getInt("version")!=1 || in.getInt("iterations")!=ITERATIONS || !"gzip".equals(in.getString("compression"))) throw new Exception("Backup format");
        byte[] salt=Base64.getDecoder().decode(in.getString("salt")),iv=Base64.getDecoder().decode(in.getString("iv"));
        if(salt.length!=16 || iv.length!=12) throw new Exception("Backup format");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,derive(password,salt),new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);
        byte[] zipped=cipher.doFinal(Base64.getDecoder().decode(in.getString("ciphertext")));
        try {String json=new String(gunzip(zipped),StandardCharsets.UTF_8);StateValidator.parse(json);return json;}finally{Arrays.fill(zipped,(byte)0);}
    }
    private BackupCodec() {}
}
