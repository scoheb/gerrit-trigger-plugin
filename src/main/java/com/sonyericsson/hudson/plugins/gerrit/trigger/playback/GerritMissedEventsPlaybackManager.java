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

package com.sonyericsson.hudson.plugins.gerrit.trigger.playback;


import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import hudson.XmlFile;

import java.io.IOException;
import java.util.Date;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.GerritPluginChecker;
import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;

/**
 * The GerritMissedEventsPlaybackManager is responsible for recording a last-alive timestamp
 * for each server connection. The motivation is that we want to be able to know when we last
 * received an event. This will help us determine upon connection startup, if we have missed
 * some events while the connection was down.
 * @author scott.hebert@ericsson.com
 */
public class GerritMissedEventsPlaybackManager implements ConnectionListener, GerritEventListener {

    private static final Logger logger = LoggerFactory.getLogger(GerritMissedEventsPlaybackManager.class);
    private static final String AUDIT_PLUGIN_NAME = "audit";

    private String serverName;
    /**
     * Map that contains timestamps.
     */
    protected TreeMap<String, Long> serverTimestamps;
    private boolean isSupported = true;

    /**
     * @param name Gerrit Server Name
     */
    public GerritMissedEventsPlaybackManager(String name) {
        this.serverName = name;
        GerritServer server = PluginImpl.getInstance().getServer(serverName);
        if (server != null && server.getConfig() != null) {
            isSupported = GerritPluginChecker.isPluginEnabled(
                    server.getConfig(), AUDIT_PLUGIN_NAME);
        }
    }

    /**
     * Load in the last-alive Timestamp file.
     * @throws IOException is we cannot unmarshal.
     */
    protected void load() throws IOException {
        XmlFile xml = PlaybackUtils.getConfigXml();
        if (xml.exists()) {
            serverTimestamps = (TreeMap<String, Long>)xml.unmarshal(serverTimestamps);
        }
    }

    /**
     * get Diff From Current Time.
     * @return diff between current time and last known
     * timestamp.
     */
    protected long getDiffFromCurrentTime() {
        long diff = 0;
        //get timestamp for server
        if (serverTimestamps != null) {
            Long myTimeStamp = serverTimestamps.get(serverName);
            logger.info("Previous alive timestamp was: " + myTimeStamp);
            if (myTimeStamp != null) {
                //print diff
                diff = new Date().getTime() - myTimeStamp;
                logger.info("Diff between then and now: " + diff);
            }
        }
        return diff;
    }
    /**
     * When the connection is established, we load in the last-alive
     * timestamp for this server and try to determine if a time range
     * exist whereby we missed some events. If so, request the events
     * from the Gerrit audit plugin and pump them in to play them back.
     */
    @Override
    public void connectionEstablished() {
        logger.info("Connection Established!");

        try {
            load();
        } catch (IOException e) {
            logger.error("Failed to load in timestamps for this server: " + e.getMessage(), e);
        }
        long diff = getDiffFromCurrentTime();
    }

    /**
     * Log when the connection goes down.
     */
    @Override
    public void connectionDown() {
        logger.info("Connection Down!");
    }

    /**
     * This allows us to persist a last known alive time
     * for the server.
     * @param event Gerrit Event
     */
    @Override
    public void gerritEvent(GerritEvent event) {
        logger.info("recording timestamp due to an event for server: {}", serverName);
        persist(new Date().getTime());
    }

    /**
     * Takes a timestamp and persists to xml file.
     * @param ts Timestamp to persist
     */
    private synchronized void persist(long ts) {
        serverTimestamps.put(serverName, ts);
        XmlFile config = PlaybackUtils.getConfigXml();
        try {
            config.write(serverTimestamps);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Shutdown the listener.
     */
    public void shutdown() {
        GerritServer server = PluginImpl.getInstance().getServer(serverName);
        if (server != null) {
            server.removeListener((GerritEventListener)this);
        } else {
            logger.error("Could not find server {}", serverName);
        }
    }

    /**
     * @return whether playback is supported.
     */
    public boolean isSupported() {
        return isSupported;
    }

}
