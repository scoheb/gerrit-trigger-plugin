package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class GerritApprovals {

    private static final Logger logger = LoggerFactory.getLogger(GerritApprovals.class);

    /**
     * A tuple of a high and a low number.
     */
    public static class HighLow {

        private final int high;
        private final int low;

        /**
         * Standard constructor.
         *
         * @param high the highest number.
         * @param low  the lowest number.
         */
        public HighLow(int high, int low) {
            this.high = high;
            this.low = low;
        }

        /**
         * Get the High number.
         *
         * @return the high number.
         */
        public int getHigh() {
            return high;
        }

        /**
         * Get the Low number.
         *
         * @return the low number.
         */
        public int getLow() {
            return low;
        }

        @Override
        public String toString() {
            return "HighLow(" + high + "," + low + ")";
        }
    }

    /**
     * Represents a "vote"-type or Approval of a change in the JSON structure.
     */
    public static enum Approval {
        /**
         * A Code Review Approval type <i>CRVW</i>.
         */
        CODE_REVIEW("Code-Review"),
        /**
         * A Verified Approval type <i>VRIF</i>.
         */
        VERIFIED("Verified");
        private String type;

        /**
         * Standard constructor.
         *
         * @param type the approval type.
         */
        Approval(String type) {
            this.type = type;
        }

        /**
         * Finds the highest and lowest approval value of the approval's type for the specified change.
         *
         * @param res the change.
         * @return the highest and lowest value. Or 0,0 if there are no values.
         */
        public HighLow getApprovals(JSONObject res) {
            logger.trace("Get Approval: {} {}", type, res);
            int highValue = Integer.MIN_VALUE;
            int lowValue = Integer.MAX_VALUE;
            if (res.has("currentPatchSet")) {
                logger.trace("Has currentPatchSet");
                JSONObject patchSet = res.getJSONObject("currentPatchSet");
                if (patchSet.has("approvals")) {
                    JSONArray approvals = patchSet.getJSONArray("approvals");
                    logger.trace("Approvals: ", approvals);
                    for (Object o : approvals) {
                        JSONObject ap = (JSONObject)o;
                        if (type.equalsIgnoreCase(ap.optString("type"))) {
                            logger.trace("A {}", type);
                            try {
                                int approval = Integer.parseInt(ap.getString("value"));
                                highValue = Math.max(highValue, approval);
                                lowValue = Math.min(lowValue, approval);
                            } catch (NumberFormatException nfe) {
                                logger.warn("Gerrit is bad at giving me Approval-numbers!", nfe);
                            }
                        }
                    }
                }
            }
            if (highValue == Integer.MIN_VALUE && lowValue == Integer.MAX_VALUE) {
                logger.debug("Returning all 0");
                return new HighLow(0, 0);
            } else {
                HighLow r = new HighLow(highValue, lowValue);
                logger.debug("Returning something {}", r);
                return r;
            }
        }
    }

    /**
     * Finds the highest and lowest code review vote for the provided patch set.
     *
     * @param res the patch set.
     * @return the highest and lowest code review vote for the patch set.
     */
    public static HighLow getCodeReview(JSONObject res) {
        return Approval.CODE_REVIEW.getApprovals(res);
    }

    /**
     * Finds the lowest and highest verified vote for the provided patch set.
     *
     * @param res the patch-set.
     * @return the highest and lowest verified vote.
     */
    public static HighLow getVerified(JSONObject res) {
        return Approval.VERIFIED.getApprovals(res);
    }

}
