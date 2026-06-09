package com.example.membershipservice.model.enums;

public enum MembershipTier {
    SILVER(1),
    GOLD(2),
    PLATINUM(3);

    private final int rank;

    MembershipTier(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
