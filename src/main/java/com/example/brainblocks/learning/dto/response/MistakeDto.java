package com.example.brainblocks.learning.dto.response;

import com.example.brainblocks.learning.model.LearnerMistake;

/** A wrong answer shown in the mistakes-review screen and the end-of-stage summary. */
public class MistakeDto {

    private final Long id;
    private final String topic;
    private final String questionText;
    private final String givenAnswer;
    private final String correctAnswer;
    private final String explanation;

    public MistakeDto(Long id, String topic, String questionText, String givenAnswer,
                      String correctAnswer, String explanation) {
        this.id = id;
        this.topic = topic;
        this.questionText = questionText;
        this.givenAnswer = givenAnswer;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
    }

    public static MistakeDto fromEntity(LearnerMistake m) {
        return new MistakeDto(m.getId(), m.getTopic().name(), m.getQuestionText(),
                m.getGivenAnswer(), m.getCorrectAnswer(), m.getExplanation());
    }

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getQuestionText() { return questionText; }
    public String getGivenAnswer() { return givenAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getExplanation() { return explanation; }
}
