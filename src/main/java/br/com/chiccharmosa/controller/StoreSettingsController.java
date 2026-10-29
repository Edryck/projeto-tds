package br.com.chiccharmosa.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import br.com.chiccharmosa.dto.StoreSettingsDto.Logo;
import br.com.chiccharmosa.dto.StoreSettingsDto.SettingsRequest;
import br.com.chiccharmosa.dto.StoreSettingsDto.SettingsResponse;
import br.com.chiccharmosa.exception.BusinessException;
import br.com.chiccharmosa.service.StoreSettingsService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/store-settings")
public class StoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    public StoreSettingsController(StoreSettingsService storeSettingsService) {
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping
    public EntityModel<SettingsResponse> get() {
        return toModel(storeSettingsService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('settings:manage')")
    public EntityModel<SettingsResponse> update(@Valid @RequestBody SettingsRequest request) {
        return toModel(storeSettingsService.update(request, Instant.now()));
    }

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('settings:manage')")
    public EntityModel<SettingsResponse> updateLogo(@RequestPart("file") MultipartFile file) {
        try {
            return toModel(storeSettingsService.updateLogo(file.getBytes(), file.getContentType(), Instant.now()));
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, StoreSettingsService.INVALID_LOGO_MESSAGE);
        }
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        Logo logo = storeSettingsService.logo()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Logo não cadastrada"));
        return ResponseEntity.ok()
                .header("Content-Type", logo.contentType())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache())
                .body(logo.content());
    }

    private EntityModel<SettingsResponse> toModel(SettingsResponse settings) {
        return EntityModel.of(settings, linkTo(methodOn(StoreSettingsController.class).get()).withSelfRel());
    }
}
