package com.example.paymentservice.chaos;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deliberately dangerous endpoints for chaos-testing exercises.
 * Only registered when the app is started with SPRING_PROFILES_ACTIVE=chaos
 * (see chaos/memory-leak.sh) — never enabled by default.
 */
@RestController
@RequestMapping("/internal/chaos")
@Profile("chaos")
public class ChaosController {

    // Deliberately never cleared except by /reset — this IS the leak.
    private static final List<byte[]> LEAKED_MEMORY = new ArrayList<>();

    @PostMapping("/leak")
    public Map<String, Object> leak(@RequestParam(defaultValue = "10") int megabytes) {
        LEAKED_MEMORY.add(new byte[megabytes * 1024 * 1024]);
        return Map.of(
                "retainedChunks", LEAKED_MEMORY.size(),
                "approxRetainedMb", LEAKED_MEMORY.size() * megabytes
        );
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        int chunks = LEAKED_MEMORY.size();
        LEAKED_MEMORY.clear();
        return Map.of("clearedChunks", chunks);
    }
}
