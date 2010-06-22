/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.Cause;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A Cause why a build was scheduled.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritCause extends Cause {

    private PatchsetCreated event;

    /**
     * Default DataBound Constructor.
     * @param event the event that triggered the build.
     */
    @DataBoundConstructor
    public GerritCause(PatchsetCreated event) {
        this.event = event;
    }

    /**
     * Default Constructor.
     */
    public GerritCause() {
    }

    /**
     * The event.
     * @return the event.
     */
    public PatchsetCreated getEvent() {
        return event;
    }

    /**
     * The event.
     * @param event the event.
     */
    public void setEvent(PatchsetCreated event) {
        this.event = event;
    }

    @Override
    public String getShortDescription() {
        String url = getUrl();
        StringBuilder str = new StringBuilder("Triggered by Gerrit: <a href=\"");
        str.append(url).append("\" target=\"_new\">").append(url).append("</a>");
        return str.toString();
    }

    /**
     * Gets the URL to the Gerrit patchSet.
     * @return the url.
     */
    public String getUrl() {
        return PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(
                event.getChange().getNumber(),
                event.getPatchSet().getNumber());
    }

    @Override
    public String toString() {
        return "GerritCause: " + event;
    }


}
