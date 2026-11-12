package com.example.brainblocks.learning.dto.response;

import com.example.brainblocks.learning.model.Topic;

import java.util.List;

/**
 * One generated question delivered to the practice screen. Includes everything the
 * client needs: the four answer options (one is correct), a teaching hint, a short
 * explanation, and an optional visual model for younger children.
 */
public class QuestionResponse {

    private final int id;
    private final Topic topic;
    private final String text;
    private final List<String> options;
    private final int correctIndex;
    private final String hint;
    private final String explanation;
    private final VisualModel visual;

    public QuestionResponse(int id, Topic topic, String text, List<String> options, int correctIndex,
                            String hint, String explanation, VisualModel visual) {
        this.id = id;
        this.topic = topic;
        this.text = text;
        this.options = options;
        this.correctIndex = correctIndex;
        this.hint = hint;
        this.explanation = explanation;
        this.visual = visual;
    }

    public int getId() { return id; }
    public Topic getTopic() { return topic; }
    public String getText() { return text; }
    public List<String> getOptions() { return options; }
    public int getCorrectIndex() { return correctIndex; }
    public String getHint() { return hint; }
    public String getExplanation() { return explanation; }
    public VisualModel getVisual() { return visual; }

    /** 2026-11-11: the skill it practices (Skills.NAMES); the client sends it back with the answer. */
    private String skill;

    public String getSkill() { return skill; }

    public QuestionResponse withSkill(String skill) {
        this.skill = skill;
        return this;
    }

    /**
     * 2026-11-12: the question's row in the bank (learn_question). The client answers by it, and the
     * server checks - with {@link #withoutAnswer()} the right answer isn't sent at all.
     */
    private Long bankId;

    public Long getBankId() { return bankId; }

    public QuestionResponse withBankId(Long bankId) {
        this.bankId = bankId;
        return this;
    }

    /** The same question with correctIndex -1 and no explanation (it may give the answer away). */
    public QuestionResponse withoutAnswer() {
        return new QuestionResponse(id, topic, text, options, -1, hint, null, visual).withSkill(skill).withBankId(bankId);
    }
}
