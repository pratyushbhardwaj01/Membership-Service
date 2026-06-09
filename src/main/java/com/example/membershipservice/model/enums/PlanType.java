package com.example.membershipservice.model.enums;

public enum PlanType {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12);

    private final int durationMonths;

    PlanType(int durationMonths) {
        this.durationMonths = durationMonths;
    }

    public int getDurationMonths() {
        return durationMonths;
    }
}
