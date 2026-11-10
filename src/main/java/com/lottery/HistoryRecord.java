package com.lottery;

import java.util.List;

public class HistoryRecord {

    private final String issue;
    private final String date;
    private final List<Integer> front;
    private final List<Integer> back;

    public HistoryRecord(String issue, String date, List<Integer> front, List<Integer> back) {
        this.issue = issue;
        this.date = date;
        this.front = front;
        this.back = back;
    }

    public String getIssue() { return issue; }
    public String getDate()  { return date; }
    public List<Integer> getFront() { return front; }
    public List<Integer> getBack()  { return back; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("第%-6s期  %s  前区:", issue, date));
        for (int n : front) sb.append(String.format(" %02d", n));
        sb.append("  后区:");
        for (int n : back) sb.append(String.format(" %02d", n));
        return sb.toString();
    }
}