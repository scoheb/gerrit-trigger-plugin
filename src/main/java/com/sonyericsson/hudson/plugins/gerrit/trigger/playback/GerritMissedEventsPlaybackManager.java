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

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.GerritPluginChecker;
import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.Util;
import hudson.XmlFile;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;


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
    private static final String AUDIT_PLUGIN_URL = "a/plugins/" + AUDIT_PLUGIN_NAME + "/change-events/";

    private String serverName;
    /**
     * Map that contains timestamps.
     */
    protected Map<String, EventTimeSlice> serverTimestamps = new ConcurrentHashMap<String, EventTimeSlice>();
    /**
     * Vector that contains received Gerrit Events.
     */
    protected Vector<ChangeBasedEvent> receivedEventCache = new Vector<ChangeBasedEvent>();
    private boolean isSupported = false;
    private boolean playBackComplete = false;

    /**
     * @param name Gerrit Server Name
     */
    public GerritMissedEventsPlaybackManager(String name) {
        this.serverName = name;
        checkIfAuditPluginSupported();
    }

    /**
     * method to verify if plugin is supported
     */
    private void checkIfAuditPluginSupported() {
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
            serverTimestamps = (ConcurrentHashMap<String, EventTimeSlice>)xml.unmarshal(serverTimestamps);
        }
    }

    /**
     * get DateRange from current and last known time.
     * @return diff between current time and last known
     * timestamp.
     */
    protected synchronized Date getDateFromTimestamp() {
        //get timestamp for server
        if (serverTimestamps != null) {
            EventTimeSlice myTimeStamp = serverTimestamps.get(serverName);
            if (myTimeStamp != null) {
                Date myDate = new Date(myTimeStamp.getTimeSlice());
                logger.info("Previous alive timestamp was: {}", myDate);
                return myDate;
            }
        }
        return new Date();
    }

    /**
     * When the connection is established, we load in the last-alive
     * timestamp for this server and try to determine if a time range
     * exist whereby we missed some events. If so, request the events
     * from the Gerrit audit plugin and pump them in to play them back.
     */
    @Override
    public void connectionEstablished() {

        playBackComplete = false;
        checkIfAuditPluginSupported();
        if (!isSupported) {
            logger.warn("Playback of missed events not supported for server {}!", serverName);
            playBackComplete = true;
            return;
        }
        logger.debug("Connection Established!");

        try {
            load();
        } catch (IOException e) {
            logger.error("Failed to load in timestamps for server {}", serverName);
            logger.error("Exception: {}", e.getMessage(), e);
            playBackComplete = true;
            return;
        }
        Date timeStampDate = getDateFromTimestamp();
        long diff = new Date().getTime() - timeStampDate.getTime();
        if (diff > 0) {
            logger.info("Non-zero date range from last-alive timestamp exists for server " + serverName
                    + ": " + Util.getPastTimeString(diff));
        } else {
            logger.info("Zero date range from last-alive timestamp for server " + serverName);
            playBackComplete = true;
            return;
        }
        try {
            Vector<ChangeBasedEvent> events = getEventsFromDateRange(timeStampDate);
            logger.info("{} missed events to process...", events.size());
            for (ChangeBasedEvent evt: events) {
                logger.info("Processing missed event {}", evt);
                if (receivedEventCache.contains(evt)) {
                    logger.info("Event already triggered...skipping trigger.");
                } else {

                    //do we have this event in the time slice?
                    EventTimeSlice slice = serverTimestamps.get(serverName);
                    long currentEventCreatedTime = ((GerritTriggeredEvent)evt).getEventCreatedOn().getTime();
                    if (slice.getTimeSlice() == currentEventCreatedTime) {
                        if (slice.getEvents().contains(evt)) {
                            logger.info("Event already triggered from time slice...skipping trigger.");
                            continue;
                        }
                    }
                    logger.info("Triggering: {}", evt);
                    PluginImpl.getInstance().getServer(serverName).triggerEvent(evt);
                    receivedEventCache.add((ChangeBasedEvent)evt);
                    logger.debug("Added event {} to received cache for server: {}", evt, serverName);
                    persist(evt);
                }
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("Error building URL for playback query: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("Error accessing URL for playback query: " + e.getMessage(), e);
        }
        playBackComplete = true;
        logger.info("Processing completed for server: {}", serverName);
    }

    /**
     * Log when the connection goes down.
     */
    @Override
    public void connectionDown() {
        logger.info("connectionDown for server: {}", serverName);
    }

    /**
     * This allows us to persist a last known alive time
     * for the server.
     * @param event Gerrit Event
     */
    @Override
    public void gerritEvent(GerritEvent event) {
        if (!isSupported()) {
            return;
        }

        if (event instanceof GerritTriggeredEvent) {
            logger.info("Recording timestamp due to an event {} for server: {}", event, serverName);
            GerritTriggeredEvent triggeredEvent = (GerritTriggeredEvent)event;
            persist(triggeredEvent);
            //add to cache
            if (!playBackComplete) {
                if (!receivedEventCache.contains(event)) {
                    receivedEventCache.add((ChangeBasedEvent)event);
                    logger.debug("Added event {} to received cache for server: {}", event, serverName);
                } else {
                    logger.debug("Event {} ALREADY in received cache for server: {}", event, serverName);
                }
            } else {
                receivedEventCache = new Vector<ChangeBasedEvent>();
                logger.debug("Playback complete...will NOT add event {} to received cache for server: {}"
                        , event, serverName);
            }
        }
    }

    /**
     * Get events for a given lower bound date.
     * @param lowerDate lower bound for which to request missed events.
     * @return collection of gerrit events.
     * @throws IOException if HTTP errors occur
     */
    protected Vector<ChangeBasedEvent> getEventsFromDateRange(Date lowerDate) throws IOException {

        GerritServer server = PluginImpl.getInstance().getServer(serverName);
        IGerritHudsonTriggerConfig config = server.getConfig();

        String events = getEventsFromAuditPlugin(config, buildAuditURL(config, lowerDate));

        return createEventsFromString(events);
    }

    /**
     * Takes a string of json events and creates a collection.
     * @param eventsString Events in json in a string.
     * @return collection of events.
     */
    private Vector<ChangeBasedEvent> createEventsFromString(String eventsString) {
        Vector<ChangeBasedEvent> events = new Vector<ChangeBasedEvent>();
        Scanner scanner = new Scanner(eventsString);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            logger.debug("found line: {}", line);
            JSONObject jsonObject = JSONObject.fromObject(line);
            GerritEvent evt = GerritJsonEventFactory.getEvent(jsonObject);
            if (evt instanceof GerritTriggeredEvent) {
                Provider provider = new Provider();
                provider.setName(serverName);
                ((GerritTriggeredEvent)evt).setProvider(provider);
            }
            events.add((ChangeBasedEvent)evt);
        }
        scanner.close();
        return events;
    }

    /**
     *
     * @param config Gerrit config for server.
     * @param url URL to use.
     * @return String of gerrit events.
     */
    protected String getEventsFromAuditPlugin(IGerritHudsonTriggerConfig config, String url) {
        logger.info("Going to GET: {}", url);
        DefaultHttpClient httpclient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url);
        if (config.getGerritProxy() != null && !config.getGerritProxy().isEmpty()) {
            try {
                URL proxyUrl = new URL(config.getGerritProxy());
                HttpHost proxy = new HttpHost(proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol());
                httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            } catch (MalformedURLException e) {
                logger.error("Could not parse proxy URL, attempting without proxy.", e);
            }
        }

        httpclient.getCredentialsProvider().setCredentials(new AuthScope(null, -1),
                config.getHttpCredentials());
        HttpResponse execute;
        try {
            execute = httpclient.execute(httpGet);
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
            return "";
        }

        int statusCode = execute.getStatusLine().getStatusCode();
        logger.info("status code: {}", statusCode);

        if (statusCode == HttpURLConnection.HTTP_OK) {
            try {
                InputStream bodyStream = execute.getEntity().getContent();
                String body = IOUtils.toString(bodyStream, "UTF-8");
                logger.info(body);
                return body;
            } catch (IOException ioe) {
                logger.warn(ioe.getMessage(), ioe);
            }
        } else {
            logger.warn("status code: {}", statusCode);
        }
        return "";
    }

    /**
     *
     * @param config Gerrit Config for server.
     * @param date1 lower bound for date range,
     * @return url to use to request missed events.
     * @throws UnsupportedEncodingException if URL encoding not supported.
     */
    protected String buildAuditURL(IGerritHudsonTriggerConfig config, Date date1)
            throws UnsupportedEncodingException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String url = AUDIT_PLUGIN_URL + "?t1=" + URLEncoder.encode(df.format(date1), "UTF-8");

        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        String restUrl = gerritFrontEndUrl;
        if (gerritFrontEndUrl != null && !gerritFrontEndUrl.endsWith("/")) {
            restUrl = gerritFrontEndUrl + "/";
        }
        return restUrl + url;
    }

    /**
     * Takes a timestamp and persists to xml file.
     * @param evt Gerrit Event to persist.
     * @return true if was able to persist event.
     */
    private synchronized boolean persist(GerritTriggeredEvent evt) {

        long ts = evt.getEventCreatedOn().getTime();

        if (ts == 0) {
            logger.warn("Event CreatedOn is 0...Gerrit Server does not support attribute eventCreateOn. "
                    + "Will NOT persist this event and Missed Events will be disabled!");
            isSupported = false;
            return false;
        }

        EventTimeSlice slice = serverTimestamps.get(serverName);
        if (slice != null && ts < slice.getTimeSlice()) {
            logger.debug("Event has same time slice {} or is earlier...NOT Updating time slice.", ts);
        } else {
            if (slice == null) {
                slice = new EventTimeSlice(ts);
                slice.addEvent(evt);
            } else {
                if (ts > slice.getTimeSlice()) {
                    logger.debug("Current timestamp {} is GREATER than slice time {}.", ts, slice.getTimeSlice());
                    slice = new EventTimeSlice(ts);
                    slice.addEvent(evt);
                } else {
                    if (ts == slice.getTimeSlice()) {
                        logger.debug("Current timestamp {} is EQUAL to slice time {}.", ts, slice.getTimeSlice());
                        slice.addEvent(evt);
                    }
                }
            }
        }

        serverTimestamps.put(serverName, slice);
        XmlFile config = PlaybackUtils.getConfigXml();
        try {
            config.write(serverTimestamps);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return true;
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

    /**
     * Return server timestamps.
     * @return map of timestamps.
     */
    public Map<String, EventTimeSlice> getServerTimestamps() {
        return serverTimestamps;
    }
}
