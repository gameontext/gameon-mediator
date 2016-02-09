package net.wasdev.gameon.mediator;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class GameOnHeaderAuth {

    public static final String SYSPROP_LOGGING = "apikey.log";
    private static final String CHAR_SET = "UTF-8";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";
    protected final String secret;
    protected final String userId;

    public GameOnHeaderAuth(String secret, String userId) {
        this.secret = secret;
        this.userId = userId;
    }

    protected String buildHmac(List<String> stuffToHash, String key)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(new SecretKeySpec(key.getBytes("UTF-8"), HMAC_ALGORITHM));
                
                StringBuffer hashData = new StringBuffer();
                for(String s: stuffToHash){
                    hashData.append(s);            
                }
                
                return Base64.getEncoder().encodeToString( mac.doFinal(hashData.toString().getBytes(CHAR_SET)) );
            }

    protected String buildHash(byte[] data) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
        md.update(data); 
        byte[] digest = md.digest();
        return Base64.getEncoder().encodeToString( digest );
    }

}