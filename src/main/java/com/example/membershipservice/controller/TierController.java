package com.example.membershipservice.controller;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import com.example.membershipservice.service.TierEvaluatorService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tiers")
public class TierController {

    private final TierEvaluatorService tierEvaluatorService;

    public TierController(TierEvaluatorService tierEvaluatorService) {
        this.tierEvaluatorService = tierEvaluatorService;
    }

    @PostMapping("/evaluate")
    public TierEvaluationResponse evaluate(@RequestBody TierCriteria criteria) {
        return new TierEvaluationResponse(tierEvaluatorService.computeTier(criteria));
    }

    public record TierEvaluationResponse(MembershipTier recommendedTier) {}
}
