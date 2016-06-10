/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.gameontext.signed;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

public class SignedRequestHmac {
    static final String UTF8 = "UTF-8";

    static final String HMAC_ALGORITHM = "HmacSHA256";
    static final String SHA_256 = "SHA-256";
    static final String GAMEON_HEADER_PREFIX = "gameon-";
    static final String GAMEON_ID = "gameon-id";
    static final String GAMEON_DATE = "gameon-date";
    static final String GAMEON_HEADERS = "gameon-sig-headers";
    static final String GAMEON_PARAMETERS = "gameon-sig-params";
    static final String GAMEON_SIG_BODY = "gameon-sig-body";
    static final String GAMEON_SIGNATURE = "gameon-signature";

    /** expiry time for requests in ms */
    static final Duration EXPIRES_REQUEST_MS = Duration.ofMinutes(5);

    /** how long to retain replays for, must be longer than the valid request period */
    static final Duration EXPIRES_REPLAY_MS = EXPIRES_REQUEST_MS .plus(Duration.ofMinutes(1));

    protected final String secret;
    protected String signature;

    protected final String method;  // 1
    protected final String baseUri; // 2
    protected final String userId;  // 3
    protected String dateString;    // 4
    protected String sigHeaders;    // 5
    protected String sigParameters; // 6
    protected String sigBody;       // 7

    protected Instant date;
    protected boolean signedRequestBody = false;

    // Temporary: cope with sigs that don't have method/uri path in them
    boolean oldStyle = false;

    public SignedRequestHmac(String userId, String secret, String method, String baseUri) {
        this.userId = userId == null ? "" : userId; // empty for unauthed request
        this.secret = secret;
        this.method = method == null ? "" : method; // empty for websocket
        this.baseUri = baseUri;

        if ( secret == null || secret.isEmpty() ) {
            throw new WebApplicationException("Invalid or unretrievable shared secret", Status.FORBIDDEN);
        }

        if ( baseUri == null ) {
            throw new NullPointerException("baseUri may not be null");
        }
    }

    /**
     * Read gameon-* header values
     * @param headers
     * @return this
     */
    public SignedRequestHmac checkHeaders(SignedRequestMap headers) {
        this.dateString = headers.getAll(GAMEON_DATE, null);
        this.date = parseValue(dateString);

        this.sigHeaders = headers.getAll(GAMEON_HEADERS, "");
        this.sigParameters = headers.getAll(GAMEON_PARAMETERS, "");

        this.sigBody = headers.getAll(GAMEON_SIG_BODY, "");
        this.signedRequestBody = !this.sigBody.isEmpty();

        this.signature = headers.getAll(GAMEON_SIGNATURE, null);
        if ( signature == null || signature.isEmpty() ) {
            throw new WebApplicationException("Invalid signature (hmac)", Status.FORBIDDEN);
        }
        return this;
    }

    /**
     * Check signature against recently seen signatures to guard against
     * replay attacks.
     *
     * @param timedCache
     * @return this
     */
    public SignedRequestHmac checkDuplicate(SignedRequestTimedCache timedCache) throws WebApplicationException {
        if ( signature != null && "POST".equals(method) &&
             timedCache.isDuplicate(signature, EXPIRES_REPLAY_MS) ) {
            throw new WebApplicationException("Duplicate request", Status.FORBIDDEN);
        }
        return this;
    }

    /**
     * Make sure we only look at young requests
     * @return this
     */
    public SignedRequestHmac checkExpiry() {
        Instant now = Instant.now();
        if (date == null) {
            throw new WebApplicationException("Invalid signature (date)", Status.FORBIDDEN);
        } else if( Duration.between(date,now).compareTo(EXPIRES_REQUEST_MS) > 0) {
            throw new WebApplicationException("Signature expired", Status.FORBIDDEN);
        }
        return this;
    }

    /**
     * Verify that the hash of additional header and parameter values matches that
     * specified in the incoming header
     *
     * @param headers Request headers
     * @param parameters Request query string
     * @return this
     */
    public SignedRequestHmac verifyRequestHeaderHashes(SignedRequestMap headers,
                                    SignedRequestMap parameters) throws WebApplicationException {
        try {
            if ( !sigHeaders.isEmpty() && hashOfValuesNotEqual(sigHeaders, headers) ) {
                throw new WebApplicationException("Invalid signature (headers)", Status.FORBIDDEN);
            }
            if ( !sigParameters.isEmpty() && hashOfValuesNotEqual(sigParameters, parameters) ) {
                throw new WebApplicationException("Invalid signature (parameters)", Status.FORBIDDEN);
            }
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new WebApplicationException("Invalid signature", Status.FORBIDDEN);
        }
        return this;
    }

    /**
     * Create A;B;C;hashOfValues values for required additional request
     * headers or query string parameters as specified by name. This will add
     * gameon-sig-headers and/or gameon-sig-params headers to the passed
     * in header list.
     *
     * @param headers Request headers: will be modified if headers or query
     *      parameters are named for inclusion in the signature
     * @param header_names Names of headers to include in hash
     * @param parameters Query parameters
     * @param parameter_names Names of query parameters to include in hash
     * @throws WebApplicationException
     * @return this
     */
    public SignedRequestHmac generateRequestHeaderHashes(SignedRequestMap headers,
                                    List<String> header_names,
                                    SignedRequestMap query_parameters,
                                    List<String> parameter_names) throws WebApplicationException{
        try {
            if ( header_names != null && !header_names.isEmpty() ) {
                sigHeaders = hashOfValues(header_names, headers);
                headers.putSingle(GAMEON_HEADERS, sigHeaders);
            }

            if ( parameter_names != null && !parameter_names.isEmpty() ) {
                 sigParameters = hashOfValues(parameter_names, query_parameters);
                headers.putSingle(GAMEON_PARAMETERS, sigParameters);
            }
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // this is our fault.
            throw new WebApplicationException("Unable to generate signature", Status.INTERNAL_SERVER_ERROR);
        }
        return this;
    }

    /**
     * @return true if a signed request body should be present
     */
    public boolean hasRequestBody() {
        return signedRequestBody;
    }

    /**
     * Given body bytes, verify the hashed header matches the expected value
     * @param body
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     * @return this
     */
    public SignedRequestHmac verifyBodyHash(byte[] body) {
        if ( sigBody.isEmpty())
            return this;

        if ( body == null || body.length == 0) {
            throw new WebApplicationException("Invalid signature (body)", Status.FORBIDDEN);
        }

        try {
            String h_bodyHash = buildHash(body);
            if ( !sigBody.equals(h_bodyHash) ) {
                throw new WebApplicationException("Invalid signature (bodyHash)", Status.FORBIDDEN);
            }
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new WebApplicationException("Invalid signature", Status.FORBIDDEN);
        }

        return this;
    }

    /**
     * Generate the ameon-sig-body header with a hash of the message body
     *
     * @param body Message body bytes
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     * @return this
     */
    public SignedRequestHmac generateBodyHash(SignedRequestMap headers, byte[] body) {
        try {
            sigBody = buildHash(body);
            headers.putSingle(GAMEON_SIG_BODY, sigBody);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new WebApplicationException("Invalid signature", Status.FORBIDDEN);
        }
        return this;
    }

    /**
     * Verify that the gameon-signature header matches the hashed value of
     * all of the signature elements(7):  method, baseUri, userId,
     * dateString, sigHeaders, sigParameters, sigBody
     */
    public SignedRequestHmac verifyFullSignature() {
        try {
            List<String> stuffToHash = new ArrayList<String>();

            if ( !oldStyle ) {
                stuffToHash.add(method);    // (1)
                stuffToHash.add(baseUri);   // (2)
            }
            stuffToHash.add(userId);        // (3)
            stuffToHash.add(dateString);    // (4)
            stuffToHash.add(sigHeaders);    // (5)
            stuffToHash.add(sigParameters); // (6)
            stuffToHash.add(sigBody);       // (7)

            String h_hmac = buildHmac(stuffToHash);
            if ( !signature.equals(h_hmac) ) {
                throw new WebApplicationException("Invalid signature (hmacCompare)", Status.FORBIDDEN);
            }
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            throw new WebApplicationException("Invalid signature", Status.FORBIDDEN);
        }

        return this;
    }

    /**
     * Set to the old-style date, based on the toString of Instant
     * @param instant
     * @return this
     */
    public SignedRequestHmac setOldStyleDate(Instant instant) {
        this.date = instant;
        this.dateString = instant.toString();
        this.oldStyle = true;
        return this;
    }

    /**
     * Set to a fixed/previously known date string
     * @param dateString
     * @return this
     */
    public SignedRequestHmac setDate(String dateString) {
        this.dateString = dateString;
        this.date = parseValue(dateString);
        return this;
    }

    /**
     * Set the date string to now.
     * @return this
     */
    public SignedRequestHmac setNow() {
        ZonedDateTime now = ZonedDateTime.now();
        this.date = now.toInstant();
        this.dateString = DateTimeFormatter.RFC_1123_DATE_TIME.format(now);
        this.oldStyle = false;
        return this;
    }

    /**
     * Generate the gameon-signature, gameon-id, and gameon-date headers.
     * @param headers
     */
    public SignedRequestHmac signRequest(SignedRequestMap headers) {

        List<String> stuffToHash = new ArrayList<String>();

        if ( dateString == null ) // fallback
            setNow();

        try {
            if ( !oldStyle ) {
                stuffToHash.add(method);                  // (1)
                stuffToHash.add(baseUri);                 // (2)
            }

            if ( !userId.isEmpty() ) {
                stuffToHash.add(userId);                  // (3)
                headers.putSingle(GAMEON_ID, userId);
            }

            stuffToHash.add(dateString);                  // (4)
            headers.putSingle(GAMEON_DATE, dateString);

            stuffToHash.add(valueOrEmpty(sigHeaders));    // (5)
            stuffToHash.add(valueOrEmpty(sigParameters)); // (6)
            stuffToHash.add(valueOrEmpty(sigBody));       // (7)

            signature = buildHmac(stuffToHash);
            headers.putSingle(GAMEON_SIGNATURE, signature);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            // this is our fault.
            throw new WebApplicationException("Unable to generate signature", Status.INTERNAL_SERVER_ERROR);
        }
        return this;
    }

    public String getSignature() {
        return signature;
    }

    /**
     * For the WebSocket protocol handshake, the server (a room) takes the previous
     * signature value, and combines that with a new date to create a new signature.
     *
     * This will set new date/signature values into new headers based on
     * previously read/calculated values.
     *
     * @param headers
     * @return
     */
    public SignedRequestHmac wsResignRequest(SignedRequestMap headers) {
        if ( signature == null )
            throw new NullPointerException("Must have a parsed signature to resign");

        try {
            List<String> stuffToHash = new ArrayList<String>();

            if ( oldStyle ) {
                setOldStyleDate(Instant.now());
            } else {
                setNow();
                stuffToHash.add(signature);               // (2) - previous signature
            }

            stuffToHash.add(0, dateString);               // (1) -- this should come first
            headers.putSingle(GAMEON_DATE, dateString);

            signature = buildHmac(stuffToHash);
            headers.putSingle(GAMEON_SIGNATURE, signature);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            // this is our fault.
            throw new WebApplicationException("Unable to generate signature", Status.INTERNAL_SERVER_ERROR);
        }

        return this;
    }


    public SignedRequestHmac wsVerifySignature(SignedRequestMap headers) {
        if ( signature == null )
            throw new NullPointerException("Must have a previous signature to verify with");

        try {
            List<String> stuffToHash = new ArrayList<String>();

            stuffToHash.add(headers.getAll(GAMEON_DATE, "")); // (1) -- read date from headers
            stuffToHash.add(signature);// (2) - what we sent

            String h_hmac = buildHmac(stuffToHash);
            String room_signature = headers.getAll(GAMEON_SIGNATURE, null);

            if ( !room_signature.equals(h_hmac) ) {
                throw new WebApplicationException("Invalid signature (hmacCompare)", Status.FORBIDDEN);
            }
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            throw new WebApplicationException("Invalid signature", Status.FORBIDDEN);
        }

        return this;
    }

//------------------------------------------------------------

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Given a list of headers/parameters names (A, B, C), construct a string that
     * contains a semi-colon separated list of names and a hash of their values:
     * A;B;C;hashOfValues
     *
     * @param names Names of message headers/parameters to include in the hash
     * @param map MultivaluedMap containing HTTP headers/parameters
     * @return A;B;C;hashOfValues
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    protected String hashOfValues(List<String> names, SignedRequestMap map) throws NoSuchAlgorithmException, UnsupportedEncodingException {

        // Make a list of values for named elements
        StringBuilder result = new StringBuilder();
        List<String> hashValues = new ArrayList<String>();

        for ( String key : names ) {
            if ( key.startsWith(GAMEON_HEADER_PREFIX) ) {
                continue;
            }
            String value = map.getAll(key, "");
            if ( value != null ) {
                result.append(key).append(';');
                hashValues.add(value);
            }
        }

        // Create a hash of the gathered values
        String hash = buildHash(hashValues);

        // Set the header to the list of names and the hash of their values
        return String.join(";", names) + ";" + hash;
    }

    /**
     * Given a header value of A;B;C;hashOfValues, makes sure a hash of values for
     * headers A, B, and C matches hashOfValues.
     * @param header
     * @param map
     * @return true if new hash generated from header values, and the old hash in the header DO NOT match.
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    protected boolean hashOfValuesNotEqual(String header, SignedRequestMap map) throws NoSuchAlgorithmException, UnsupportedEncodingException  {
        int rpos = header.lastIndexOf(';');
        String oldHash = header.substring(rpos+1);
        List<String> names = Arrays.asList(header.substring(0, rpos).split(";"));

        List<String> values = new ArrayList<String>();
        for(String key : names ) {
            values.add(map.getAll(key, ""));
        }

        String newHash = buildHash(values);
        return !oldHash.equals(newHash);
    }

    /**
     * Construct a new hmac signature using the values passed in stuffToHash
     *
     * @param stuffToHash
     * @param key
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws UnsupportedEncodingException
     */
    protected String buildHmac(List<String> stuffToHash)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(UTF8), HMAC_ALGORITHM));

        if ( oldStyle ) {
            StringBuilder hashData = new StringBuilder();
            for(String s: stuffToHash){
                hashData.append(s);
            }
            mac.update(hashData.toString().getBytes(UTF8));
        } else {
            for(String s: stuffToHash){
                mac.update(s.getBytes(UTF8));
            }
        }

        return Base64.getEncoder().encodeToString( mac.doFinal() );
    }

    protected String buildHash(String data) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        return buildHash(data.getBytes(UTF8));
    }

    protected String buildHash(byte[] data) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        MessageDigest md = MessageDigest.getInstance(SHA_256);
        md.update(data);
        byte[] digest = md.digest();
        return Base64.getEncoder().encodeToString( digest );
    }

    protected String buildHash(List<String> values) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        MessageDigest md = MessageDigest.getInstance(SHA_256);
        for( String value : values ) {
            md.update(value.getBytes(UTF8));
        }
        return Base64.getEncoder().encodeToString( md.digest() );
    }

    private Instant parseValue(String dateString) {
        try {
            ZonedDateTime then = ZonedDateTime.parse(dateString, DateTimeFormatter.RFC_1123_DATE_TIME);
            oldStyle = false; // TEMPORARY
            return then.toInstant();
        } catch(DateTimeParseException e) {
            try {
                Instant then = Instant.parse(dateString);
                oldStyle = true; // TEMPORARY: skip method and URI when parsing signature
                return then;
            } catch(DateTimeParseException ne) {
                return null;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SignedRequestHmac ")
            .append("[method=").append(method)
            .append(", baseUri=").append(baseUri)
            .append(", userId=").append(userId)
            .append(", dateString=").append(dateString)
            .append(", sigHeaders=").append(sigHeaders)
            .append(", sigParameters=").append(sigParameters)
            .append(", sigBody=").append(sigBody)
            .append(", signature=").append(signature)
            .append(", signedRequestBody=").append(signedRequestBody)
            .append(", oldStyle=").append(oldStyle).append("]");
        return builder.toString();
    }
}
