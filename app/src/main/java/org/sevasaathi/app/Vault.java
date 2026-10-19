package org.sevasaathi.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

/** Authenticated, atomically-written local vault. Android's device key is never exported. */
public final class Vault {
    private static final Object LOCK=new Object();
    private static final String ALIAS="seva-saathi-vault-v1";
    private static final byte[] AAD="seva-saathi-local-v1".getBytes(StandardCharsets.UTF_8);
    private final AtomicFile file;
    public Vault(Context context) { file=new AtomicFile(new File(context.getFilesDir(),"journal.vault")); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        // Never silently generate a different key for an existing encrypted journal.
        if(file.getBaseFile().exists() || new File(file.getBaseFile()+".bak").exists())throw new Exception("Vault key is unavailable");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    public JSONObject load() throws Exception {
        synchronized(LOCK) {
            if(!file.getBaseFile().exists() && !new File(file.getBaseFile()+".bak").exists())return null;
            byte[] bytes;
            try(java.io.FileInputStream in=file.openRead()){bytes=BackupCodec.readLimited(in,3*1024*1024);}
            if(bytes.length<33 || bytes[0]!='S' || bytes[1]!='S' || bytes[2]!='V' || bytes[3]!='1')throw new Exception("Invalid vault");
            byte[] iv=Arrays.copyOfRange(bytes,4,16);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);
            byte[] plain=cipher.doFinal(bytes,16,bytes.length-16);
            try{return StateValidator.parse(new String(BackupCodec.gunzip(plain),StandardCharsets.UTF_8));}finally{Arrays.fill(plain,(byte)0);}
        }
    }
    public void save(String json) throws Exception {
        synchronized(LOCK) {
            StateValidator.parse(json);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(AAD);
            byte[] compressed=BackupCodec.gzip(json.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted;
            try{encrypted=cipher.doFinal(compressed);}finally{Arrays.fill(compressed,(byte)0);}
            FileOutputStream out=null;
            try{out=file.startWrite();out.write(new byte[]{'S','S','V','1'});out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);}
            catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
        }
    }
    /** Explicit restore permits recovery after key loss without touching the old vault first. */
    public void restore(String json) throws Exception {
        synchronized(LOCK) {
            StateValidator.parse(json);
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(!store.containsAlias(ALIAS)){
                KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
                g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());g.generateKey();
            }
            save(json);
        }
    }
}
