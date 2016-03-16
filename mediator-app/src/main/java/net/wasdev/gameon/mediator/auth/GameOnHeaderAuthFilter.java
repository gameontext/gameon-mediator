package net.wasdev.gameon.mediator.auth;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Level;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;

/**
 * Outbound Client request filter. Used by {@link MapClient} to authenticate with the map service.
 */
public class GameOnHeaderAuthFilter extends GameOnHeaderAuth implements ClientRequestFilter {

    public GameOnHeaderAuthFilter(String userId, String secret) {
        super(secret,userId);
        if (secret == null)
            throw new RuntimeException("NULL secret");
    }

    @Override
    public void filter(ClientRequestContext context) throws IOException {
        try {
            // create the timestamp
            Instant now = Instant.now();
            String dateValue = now.toString();

            // create the signature
            String hmac = buildHmac(Arrays.asList(new String[] { userId, dateValue }), secret);

            MultivaluedMap<String, Object> headers = context.getHeaders();
            headers.add("gameon-id", userId);
            headers.add("gameon-date", dateValue);
            headers.add("gameon-signature", hmac);
            
            Log.log(Level.FINER, this, "Added headers to request to {0} for id {1}", context.getUri(), userId);

        } catch (Exception e) {
            Log.log(Level.SEVERE, this, "Error calculating hmac headers", e);
            throw new IOException(e);
        }
    }
}
