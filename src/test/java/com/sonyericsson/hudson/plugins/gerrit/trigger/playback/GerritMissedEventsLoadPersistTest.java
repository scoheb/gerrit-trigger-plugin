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
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.GerritPluginChecker;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.XmlFile;
import jenkins.model.Jenkins;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Random;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;

/**
 * @author scott.hebert@ericsson.com
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Jenkins.class, PluginImpl.class, PlaybackUtils.class, GerritPluginChecker.class })
@PowerMockIgnore("javax.net.ssl.*")
public class GerritMissedEventsLoadPersistTest {

    private static final int MAXRANDOMNUMBER = 100;
    private static final int SLEEPTIME = 500;

    /**
     * Default constructor.
     */
    public GerritMissedEventsLoadPersistTest() {
    }

    /**
     * Create ReplicationQueueTaskDispatcher with a mocked GerritHandler.
     */
    @Before
    public void setUp() {
        Jenkins jenkinsMock = mock(Jenkins.class);
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkinsMock);

        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        when(plugin.getServer(any(String.class))).thenReturn(server);
        GerritHandler handler = mock(GerritHandler.class);
        when(plugin.getHandler()).thenReturn(handler);
        when(server.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        PowerMockito.mockStatic(PlaybackUtils.class);

        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("gerrit-server-timestamps", ".xml");
        } catch (IOException e) {
            fail("Failed to create Temp File");
        }
        tmpFile.deleteOnExit();
        PrintWriter out = null;
        try {
            out = new PrintWriter(tmpFile);
        } catch (FileNotFoundException e) {
            fail("Failed to write to Temp File");
        }
        String text = "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<concurrent-hash-map>\n"
                + "  <entry>\n"
                + "    <string>MyGerritServer</string>\n"
                + "    <long>1415906575128</long>\n"
                + "  </entry>\n"
                + "</concurrent-hash-map>";
        out.println(text);
        out.close();
        XmlFile xmlFile = new XmlFile(tmpFile);
        PowerMockito.when(PlaybackUtils.getConfigXml()).thenReturn(xmlFile);

        PowerMockito.mockStatic(GerritPluginChecker.class);
        PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
                , anyString())).thenReturn(true);
    }

    /**
     * Given a non-existing timestamp file
     * When we attempt to load it
     * Then we retrieve a null map.
     */
    @Test
    public void testLoadTimeStampFromNonExistentFile() {

        PlaybackUtils.getConfigXml().delete();

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertEquals(missingEventsPlaybackManager.serverTimestamps.size(), 0);

    }

    /**
     * Given an existing timestamp file
     * And it contains at least one entry with a valid timestamp
     * When we attempt to load it
     * Then we retrieve a non-null map.
     */
    @Test
    public void testLoadTimeStampFromFile() {

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertNotNull(missingEventsPlaybackManager.serverTimestamps);
        assertEquals(1, missingEventsPlaybackManager.serverTimestamps.size());

    }

    /**
     * Given an existing timestamp file
     * And it contains at least one entry with a valid timestamp
     * When a new event is received for the server connection
     * Then the timestamp is persisted.
     */
    @Test
    public void testPersistTimeStampToFile() {

        Random randomGenerator = new Random();
        int randomInt = randomGenerator.nextInt(MAXRANDOMNUMBER);
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager(new Integer(randomInt).toString() + "-server");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/heads/master");
        patchsetCreated.setReceivedOn(System.currentTimeMillis());

        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        assertEquals(2, missingEventsPlaybackManager.serverTimestamps.size());
    }

    /**
     * Return a missingEventsPlaybackManager.
     * @return missingEventsPlaybackManager.
     */
    private GerritMissedEventsPlaybackManager setupManager() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertNotNull(missingEventsPlaybackManager.serverTimestamps);
        assertEquals(1, missingEventsPlaybackManager.serverTimestamps.size());

        assertTrue("should be true", missingEventsPlaybackManager.isSupported());

        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/heads/master");
        patchsetCreated.setReceivedOn(System.currentTimeMillis());

        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        patchsetCreated.setReceivedOn(System.currentTimeMillis());
        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        try {
            Thread.currentThread().sleep(SLEEPTIME);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        missingEventsPlaybackManager.connectionDown();
        missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }
        return missingEventsPlaybackManager;
    }
    /**
     * Given an existing timestamp file
     * When a connection is restarted
     * Then the diff between last timestamp and current time
     * should be greater than 0.
     */
    @Test
    public void testGetTimeStampDiff() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        assertNotNull(missingEventsPlaybackManager.serverTimestamps);
        TestCase.assertTrue("Diff should be greater than 0",
                new Date().getTime() - missingEventsPlaybackManager.getDateFromTimestamp().getTime() > 0);

        missingEventsPlaybackManager.shutdown();
    }

    /**
     * Loads a resource from the current class'
     * classpath and returns its path.
     * @param resource resource to get path for.
     * @return path to resource.
     */
    private String getResource(String resource) {
        URL csvFile = getClass().getResource(resource);
        assertNotNull("Test file missing", csvFile);
        Path csvFilePath = null;
        try {
            csvFilePath = Paths.get(csvFile.toURI());
        } catch (URISyntaxException e) {
            assertNotNull("should not throw exception", e);
        }
        return csvFilePath.toString();
    }

}
