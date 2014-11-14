/*
 * The MIT License
 *
 * Copyright (c) 2014 Ericsson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Helper to determine if a Gerrit Plugin is installed
 * on the Gerrit server.
 * @author scott.hebert@ericsson.com
 */
public final class GerritPluginChecker {

    /**
     * Gerrit Plugin Checker.
     */
    private GerritPluginChecker() {
    }

    private static final Logger logger = LoggerFactory.getLogger(GerritPluginChecker.class);

    /**
     * Query Gerrit to determine if plugin is enabled.
     * @param config Gerrit Server Config
     * @param pluginName The Gerrit Plugin name.
     * @return true if enabled.
     */
    public static boolean isPluginEnabled(IGerritHudsonTriggerConfig config, String pluginName) {
        if (config == null) {
            return false;
        }

        if (!config.isUseRestApi()) {
            logger.info("REST API is not enabled. Cannot verify if Gerrit " + pluginName + " plugin is enabled.");
            return false;
        }

        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        String restUrl = gerritFrontEndUrl;
        if (gerritFrontEndUrl != null && !gerritFrontEndUrl.endsWith("/")) {
            restUrl = gerritFrontEndUrl + "/";
        }

        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(restUrl + "plugin/" + pluginName);
        httpclient.getCredentialsProvider().setCredentials(new AuthScope(null, -1),
                new UsernamePasswordCredentials(config.getGerritHttpUserName(),
                        config.getGerritHttpPassword()));
        HttpResponse execute;
        try {
            execute = httpclient.execute(httpGet);
        } catch (IOException e) {
            logger.warn("Not able to verify if Gerrit plugin " + pluginName + " is installed. Error: " + e.getMessage());
            return false;
        }

        int statusCode = execute.getStatusLine().getStatusCode();
        switch(statusCode) {
            case HttpURLConnection.HTTP_OK:
                logger.info("Gerrit Plugin " + pluginName + " is installed");
                return true;
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                logger.warn("Not able to verify if Gerrit plugin " + pluginName
                        + " is installed. Error: " + Messages.HttpConnectionUnauthorized());
            default:
                logger.warn("Not able to verify if Gerrit plugin " + pluginName
                        + " is installed. Error: " + Messages.HttpConnectionError(statusCode));
        }
        return false;
    }
}
