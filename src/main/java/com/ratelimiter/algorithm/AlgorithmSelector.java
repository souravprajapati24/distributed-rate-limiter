package com.ratelimiter.algorithm;

import com.ratelimiter.domain.enums.AlgorithmType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AlgorithmSelector {

    private final Map<AlgorithmType, RateLimitAlgorithm> algorithms;

    public AlgorithmSelector(List<RateLimitAlgorithm> algorithmList) {
        this.algorithms = algorithmList.stream()
                .collect(Collectors.toMap(RateLimitAlgorithm::getType, Function.identity()));

        log.info("AlgorithmSelector initialized with {} algorithm(s): {}",
                algorithms.size(), algorithms.keySet());
    }

    public RateLimitAlgorithm select(AlgorithmType type) {
        RateLimitAlgorithm algorithm = algorithms.get(type);
        if (algorithm == null) {
            log.warn("No RateLimitAlgorithm implementation registered for type {}. " +
                    "Falling back to FIXED_WINDOW. This is expected for tiers " +
                    "using SLIDING_WINDOW, TOKEN_BUCKET, or LEAKY_BUCKET.", type);
            return algorithms.get(AlgorithmType.FIXED_WINDOW);
        }
        return algorithm;
    }
}