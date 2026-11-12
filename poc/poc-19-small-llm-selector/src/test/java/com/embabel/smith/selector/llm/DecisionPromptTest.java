package com.embabel.smith.selector.llm;

import static com.embabel.smith.selector.llm.DecisionPrompt.Format.ID;
import static com.embabel.smith.selector.llm.DecisionPrompt.Format.OOD;
import static org.assertj.core.api.Assertions.assertThat;

import com.embabel.smith.retrieval.SpecEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

class DecisionPromptTest {

    private static final List<SpecEntry> THREE = List.of(
            new SpecEntry("http-client", "http-client", "A reusable HTTP client wrapper for REST calls.",
                    List.of("UserInput"), "HttpResponse"),
            new SpecEntry("retry-policy", "retry-policy", "Adds retry-with-backoff behaviour to failing operations.",
                    List.of("Operation"), "Operation"),
            new SpecEntry("http-mock", "http-mock", "Stubs outgoing HTTP calls in tests.",
                    List.of("TestContext"), "MockServer"));

    @Test
    void idPromptListsLetteredOptionsInOrder() {
        String prompt = DecisionPrompt.build(ID, "Add retries to the HTTP client", THREE);
        assertThat(prompt)
                .contains("Request: \"Add retries to the HTTP client\"")
                .contains("A. http-client — A reusable HTTP client wrapper for REST calls.")
                .contains("B. retry-policy — Adds retry-with-backoff behaviour to failing operations.")
                .contains("C. http-mock — Stubs outgoing HTTP calls in tests.")
                .contains("Answer with the single letter of the correct option.");
        assertThat(prompt.indexOf("A. http-client")).isLessThan(prompt.indexOf("B. retry-policy"));
        assertThat(prompt.indexOf("B. retry-policy")).isLessThan(prompt.indexOf("C. http-mock"));
    }

    @Test
    void oodPromptListsNumberedOptionsWithoutTheLetterContract() {
        String prompt = DecisionPrompt.build(OOD, "Add retries to the HTTP client", THREE);
        assertThat(prompt)
                .contains("User request: \"Add retries to the HTTP client\"")
                .contains("1. http-client — A reusable HTTP client wrapper for REST calls.")
                .contains("2. retry-policy — Adds retry-with-backoff behaviour to failing operations.")
                .contains("3. http-mock — Stubs outgoing HTTP calls in tests.")
                .contains("Reply with only the number of the best spec.")
                .doesNotContain("single letter");
    }

    @Test
    void idReplyParsesTheSingleLetterToItsCandidate() {
        assertThat(DecisionPrompt.parse(ID, "B", THREE)).isEqualTo("retry-policy");
        assertThat(DecisionPrompt.parse(ID, "a", THREE)).isEqualTo("http-client");
        assertThat(DecisionPrompt.parse(ID, "C\n", THREE)).isEqualTo("http-mock");
    }

    @Test
    void idReplyThatNamesNoCandidateIsNoSelection() {
        assertThat(DecisionPrompt.parse(ID, "Z", THREE)).isNull();
        assertThat(DecisionPrompt.parse(ID, "I don't know", THREE)).isNull();
        assertThat(DecisionPrompt.parse(ID, "", THREE)).isNull();
        assertThat(DecisionPrompt.parse(ID, null, THREE)).isNull();
    }

    @Test
    void oodReplyParsesTheNumberToItsCandidate() {
        assertThat(DecisionPrompt.parse(OOD, "2", THREE)).isEqualTo("retry-policy");
        assertThat(DecisionPrompt.parse(OOD, "The best is 3.", THREE)).isEqualTo("http-mock");
    }

    @Test
    void oodReplyFallsBackToACandidateName() {
        assertThat(DecisionPrompt.parse(OOD, "retry-policy", THREE)).isEqualTo("retry-policy");
        assertThat(DecisionPrompt.parse(OOD, "I would pick http-mock here.", THREE)).isEqualTo("http-mock");
    }

    @Test
    void oodReplyOutsideTheOptionRangeIsNoSelection() {
        assertThat(DecisionPrompt.parse(OOD, "9", THREE)).isNull();
        assertThat(DecisionPrompt.parse(OOD, "0", THREE)).isNull();
        assertThat(DecisionPrompt.parse(OOD, "no spec fits", THREE)).isNull();
    }

    @Test
    void emptyCandidateSetYieldsNoSelection() {
        assertThat(DecisionPrompt.parse(ID, "A", List.of())).isNull();
        assertThat(DecisionPrompt.parse(OOD, "1", List.of())).isNull();
    }
}
