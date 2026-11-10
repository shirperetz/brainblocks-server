package com.example.brainblocks.dto.request;

import java.util.List;

public class CreateRaceRoomRequest {

    private String name;
    private String teacherKey;
    private int maxPlayers;
    private int minPlayers;
    private int trackLength;
    private boolean requiresApproval;
    private List<String> operations;
    private List<String> numberTypes;
    private String difficulty;
    private int questionTimeLimit;
    private boolean manualMode;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTeacherKey() { return teacherKey; }
    public void setTeacherKey(String teacherKey) { this.teacherKey = teacherKey; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }
    public int getTrackLength() { return trackLength; }
    public void setTrackLength(int trackLength) { this.trackLength = trackLength; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public List<String> getOperations() { return operations; }
    public void setOperations(List<String> operations) { this.operations = operations; }
    public List<String> getNumberTypes() { return numberTypes; }
    public void setNumberTypes(List<String> numberTypes) { this.numberTypes = numberTypes; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public int getQuestionTimeLimit() { return questionTimeLimit; }
    public void setQuestionTimeLimit(int questionTimeLimit) { this.questionTimeLimit = questionTimeLimit; }
    public boolean isManualMode() { return manualMode; }
    public void setManualMode(boolean manualMode) { this.manualMode = manualMode; }
}
