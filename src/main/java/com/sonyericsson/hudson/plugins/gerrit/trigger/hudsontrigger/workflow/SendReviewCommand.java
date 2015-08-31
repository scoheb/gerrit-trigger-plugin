package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.workflow;

import java.io.IOException;
import java.io.PrintStream;

import com.sonymobile.tools.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonymobile.tools.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;

public class SendReviewCommand extends AbstractSendCommandJob {

    private String command;
    private PrintStream printStream;

    protected SendReviewCommand(GerritConnectionConfig2 config, PrintStream printStream, String command) {
        super(config);
        this.command = command;
        this.printStream = printStream;
    }

    @Override
    public void run() {
        try {
            printStream.println(sendCommand2(command));
        } catch (IOException e) {
            printStream.println("Error: " + e.getMessage());
        }
    }

}
