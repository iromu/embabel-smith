package com.embabel.smith.studio.rest.dto;

/**
 * A run request (the Run pane): the skill source, the exported goal to run, the input, an optional
 * planner override, and the trust tier. A blank goal runs the source's single exported goal.
 */
public record RunRequest(String source, String goal, String input, String planner, String trustTier) {
}
