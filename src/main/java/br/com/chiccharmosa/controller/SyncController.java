package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.dto.SyncDto.PullResponse;
import br.com.chiccharmosa.dto.SyncDto.PushRequest;
import br.com.chiccharmosa.dto.SyncDto.PushResponse;
import br.com.chiccharmosa.security.CurrentUser;
import br.com.chiccharmosa.service.SyncService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    @PreAuthorize("hasAuthority('sync:use')")
    public PushResponse push(@Valid @RequestBody PushRequest request) {
        return syncService.push(request, CurrentUser.current());
    }

    @GetMapping("/pull")
    @PreAuthorize("hasAuthority('sync:use')")
    public PullResponse pull(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) UUID afterId, @RequestParam(defaultValue = "200") int size) {
        return syncService.pull(since, afterId, size, CurrentUser.current());
    }
}
