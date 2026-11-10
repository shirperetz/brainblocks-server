package com.example.brainblocks.learning.dto.response;

/**
 * A small, declarative description of a visual explanation. The kids client renders it
 * with emoji (e.g. 🍎🍎🍎 ➕ 🍎🍎 = 5). Only the fields relevant to {@code type} are meaningful.
 *
 * type: "OBJECTS" (a + b items) | "TAKE_AWAY" (a items, b removed) | "GROUPS" (a groups of b) | null.
 */
public class VisualModel {

    private final String type;
    private final String emoji;
    private final int a;
    private final int b;
    private final int result;
    private final String op;

    public VisualModel(String type, String emoji, int a, int b, int result, String op) {
        this.type = type;
        this.emoji = emoji;
        this.a = a;
        this.b = b;
        this.result = result;
        this.op = op;
    }

    public String getType() { return type; }
    public String getEmoji() { return emoji; }
    public int getA() { return a; }
    public int getB() { return b; }
    public int getResult() { return result; }
    public String getOp() { return op; }
}
