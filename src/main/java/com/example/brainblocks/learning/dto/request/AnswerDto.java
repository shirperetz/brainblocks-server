package com.example.brainblocks.learning.dto.request;

/**
 * One answered question inside a submitted session. The client reports what happened;
 * the server recomputes points/stars and stores wrong ones as mistakes. (v1 trusts the
 * client's `correct` flag — acceptable for a single-player kids game.)
 */
public class AnswerDto {

    private String questionText;
    private String chosenAnswer;
    private String correctAnswer;
    private boolean correct;
    private boolean hintUsed;
    private String topic;        // per-question topic (matters for MIXED stages); may be null
    private String explanation;  // shown again in the mistakes review

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getChosenAnswer() { return chosenAnswer; }
    public void setChosenAnswer(String chosenAnswer) { this.chosenAnswer = chosenAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public boolean isHintUsed() { return hintUsed; }
    public void setHintUsed(boolean hintUsed) { this.hintUsed = hintUsed; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
