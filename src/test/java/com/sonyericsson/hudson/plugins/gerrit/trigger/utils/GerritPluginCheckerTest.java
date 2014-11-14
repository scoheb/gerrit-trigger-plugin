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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;


/**
 * Created by scott.hebert@ericsson.com on 11/14/14.
 */
//@RunWith(PowerMockRunner.class)
//@PowerMockIgnore("javax.net.ssl.*")
public class GerritPluginCheckerTest  {

    /**
     * An instance of WireMock Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: WireMockRule.
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(8089); // No-args constructor defaults to port 8089
    private final String testServer = "server";
    private static final int HTTPOK = 200;
    private static final int HTTPUNAUTH = 403;
    private static final int HTTPNOTFOUND = 404;

    /**
     * Given a Gerrit server
     * When a request is made for various plugins
     * Then a result of true or false is returned.
     */
    @Test
    public void testExistingPlugin() {
        stubFor(get(urlEqualTo("/plugin/audit"))
                .willReturn(aResponse()
                        .withStatus(HTTPOK)
                        .withHeader("Content-Type", "text/html")
                        .withBody("ok")));

        stubFor(get(urlEqualTo("/plugin/unknown"))
                .willReturn(aResponse()
                        .withStatus(HTTPNOTFOUND)
                        .withHeader("Content-Type", "text/html")
                        .withBody("nope")));

        stubFor(get(urlEqualTo("/plugin/private"))
                .willReturn(aResponse()
                        .withStatus(HTTPUNAUTH)
                        .withHeader("Content-Type", "text/html")
                        .withBody("private!")));

        MockPluginCheckerConfig config = new MockPluginCheckerConfig();
        config.setGerritFrontEndURL("http://localhost:8089");
        config.setUseRestApi(true);
        config.setGerritHttpUserName("user");
        config.setGerritHttpPassword("passwd");

        boolean pluginInstalled = GerritPluginChecker.isPluginEnabled(config, "audit");
        assertTrue(pluginInstalled);
        boolean pluginNotInstalled = GerritPluginChecker.isPluginEnabled(config, "unknown");
        assertFalse(pluginNotInstalled);
        boolean pluginNotAuthorized = GerritPluginChecker.isPluginEnabled(config, "private");
        assertFalse(pluginNotAuthorized);
    }
}
