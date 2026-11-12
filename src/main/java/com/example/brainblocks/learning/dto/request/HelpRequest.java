package com.example.brainblocks.learning.dto.request;

/** "לא הבנתי" for one question. Everything here comes from the game itself, not typed by the child. */
public class HelpRequest {
    private String questionText;
    private String correctAnswer;
    private String chosenAnswer;
    private String grade;
    private boolean again;
    private Integer attempt;
    private String fallbackHint;
    /** 2026-11-12: the bank row - the server fills the right answer itself (the phone doesn't have it). */
    private Long bankId;
    public Long getBankId() { return bankId; }
    public void setBankId(Long v) { this.bankId = v; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String v) { this.questionText = v; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String v) { this.correctAnswer = v; }
    public String getChosenAnswer() { return chosenAnswer; }
    public void setChosenAnswer(String v) { this.chosenAnswer = v; }
    public String getGrade() { return grade; }
    public void setGrade(String v) { this.grade = v; }
    public boolean isAgain() { return again; }
    public void setAgain(boolean v) { this.again = v; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer v) { this.attempt = v; }
    public String getFallbackHint() { return fallbackHint; }
    public void setFallbackHint(String v) { this.fallbackHint = v; }
}
