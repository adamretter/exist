/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.persistentlogin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.Base64Encoder;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DurationValue;

import java.security.SecureRandom;
import java.util.*;

/**
 * A persistent login feature ("remember me") similar to the implementation in <a href="https://github.com/SpringSource/spring-security">Spring Security</a>,
 * which is based on <a href="http://jaspan.com/improved_persistent_login_cookie_best_practice">Improved Persistent Login Cookie
 * Best Practice</a> .
 *
 * The one-time tokens generated by this class are purely random and do not contain a user name or other information. For security reasons,
 * tokens and user information are not stored anywhere, so if the database is shut down, registered tokens will be gone.
 *
 * The one-time token approach has the negative effect that requests need to be made in sequence, which is sometimes difficult if an app uses
 * concurrent AJAX requests. Unfortunately, this is the price we have to pay for a sufficiently secure protection against
 * cookie stealing attacks.
 *
 * @author Wolfgang Meier
 */
public class PersistentLogin {

    private final static PersistentLogin instance = new PersistentLogin();

    public static PersistentLogin getInstance() {
        return instance;
    }

    private final static Logger LOG = LogManager.getLogger(PersistentLogin.class);

    public final static int DEFAULT_SERIES_LENGTH = 16;

    public final static int DEFAULT_TOKEN_LENGTH = 16;

    public final static int INVALIDATION_TIMEOUT = 20000;

    private Map<String, LoginDetails> seriesMap = Collections.synchronizedMap(new HashMap<>());

    private SecureRandom random;

    public PersistentLogin() {
        random = new SecureRandom();
    }

    /**
     * Register the user and generate a first login token which will be valid for the next
     * call to {@link #lookup(String)}.
     *
     * The generated token will have the format base64(series-hash):base64(token-hash).
     *
     * @param user the user name
     * @param password the password
     * @param timeToLive timeout of the token
     * @return a first login token
     * @throws XPathException if a query error occurs
     */
    public LoginDetails register(String user, String password, DurationValue timeToLive) throws XPathException {
        DateTimeValue now = new DateTimeValue(new Date());
        DateTimeValue expires = (DateTimeValue) now.plus(timeToLive);
        LoginDetails login = new LoginDetails(user, password, timeToLive, expires.getTimeInMillis());
        seriesMap.put(login.getSeries(), login);
        return login;
    }

    /**
     * Look up the given token and return login details. If the token is found, it will be updated
     * with a new hash before returning and the old hash is removed.
     *
     * @param token the token string provided by the user
     * @return login details for the user or null if no session was found or it was expired
     * @throws XPathException series matched but the token not. may indicate a cookie theft attack
     * or an out-of-sequence request.
     */
    public LoginDetails lookup(String token) throws XPathException {
        String[] tokens = token.split(":");

        LoginDetails data = seriesMap.get(tokens[0]);
        if (data == null) {
            LOG.debug("No session found for series " + tokens[0]);
            return null;
        }
        long now = System.currentTimeMillis();
        if (now > data.expires) {
            LOG.debug("Persistent session expired");
            seriesMap.remove(tokens[0]);
            return null;
        }

    // sequential token checking is disabled by default
    if (data.seqBehavior) {
        LOG.debug("Using sequential tokens");
        if (!data.checkAndUpdateToken(tokens[1])) {
            LOG.debug("Out-of-sequence request or cookie theft attack. Deleting session.");
            seriesMap.remove(tokens[0]);
            throw new XPathException("Token mismatch. This may indicate an out-of-sequence request (likely) or a cookie theft attack.  " +
                    "Session is deleted for security reasons.");
        }
    }

        return data;
    }

    /**
     * Invalidate the session associated with the token string. Looks up the series hash
     * and deletes it.
     *
     * @param token token string provided by the user
     */
    public void invalidate(String token) {
        String[] tokens = token.split(":");
        seriesMap.remove(tokens[0]);
    }

    private String generateSeriesToken() {
        byte[] newSeries = new byte[DEFAULT_SERIES_LENGTH];
        random.nextBytes(newSeries);

        Base64Encoder encoder = new Base64Encoder();
        encoder.translate(newSeries);
        return new String(encoder.getCharArray());
    }

    private String generateToken() {
        byte[] newSeries = new byte[DEFAULT_TOKEN_LENGTH];
        random.nextBytes(newSeries);

        Base64Encoder encoder = new Base64Encoder();
        encoder.translate(newSeries);
        return new String(encoder.getCharArray());
    }

    public class LoginDetails {

        private String userName;
        private String password;
        private String token;
        private String series;
        private long expires;
        private DurationValue timeToLive;

        // disable sequential token checking by default
        private boolean seqBehavior = false;

        private Map<String, Long> invalidatedTokens = new HashMap<>();

        public LoginDetails(String user, String password, DurationValue timeToLive, long expires) {
            this.userName = user;
            this.password = password;
            this.timeToLive = timeToLive;
            this.expires = expires;
            this.token = generateToken();
            this.series = generateSeriesToken();
        }

        public String getToken() {
            return this.token;
        }

        public String getSeries() {
            return this.series;
        }

        public String getUser() {
            return this.userName;
        }

        public String getPassword() {
            return this.password;
        }

        public DurationValue getTimeToLive() {
            return timeToLive;
        }

        public boolean checkAndUpdateToken(String token) {
            if (this.token.equals(token)) {
                update();
                return true;
            }
            // check map of invalidating tokens
            Long timeout = invalidatedTokens.get(token);
            if (timeout == null)
                return false;
            // timed out: remove
            if (System.currentTimeMillis() > timeout) {
                invalidatedTokens.remove(token);
                return false;
            }
            // found invalidating token: return true but do not replace token
            return true;
        }

        public String update() {
            timeoutCheck();
            // leave a small time window until previous token is deleted
            // to allow for concurrent requests
            invalidatedTokens.put(this.token, System.currentTimeMillis() + INVALIDATION_TIMEOUT);
            this.token = generateToken();
            return this.token;
        }

        private void timeoutCheck() {
            long now = System.currentTimeMillis();
            invalidatedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        }

        @Override
        public String toString() {
            return this.series + ":" + this.token;
        }
    }
}
