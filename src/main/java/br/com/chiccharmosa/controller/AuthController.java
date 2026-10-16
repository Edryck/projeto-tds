package br.com.chiccharmosa.controller;

import br.com.chiccharmosa.assembler.MeAssembler;
import br.com.chiccharmosa.dto.AuthDto.LoginRequest;
import br.com.chiccharmosa.dto.AuthDto.MeResponse;
import br.com.chiccharmosa.dto.AuthDto.RefreshRequest;
import br.com.chiccharmosa.dto.AuthDto.TokenResponse;
import br.com.chiccharmosa.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final MeAssembler meAssembler;

    public AuthController(AuthService authService, MeAssembler meAssembler) {
        this.authService = authService;
        this.meAssembler = meAssembler;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout();
    }

    @GetMapping("/me")
    public EntityModel<MeResponse> me() {
        return meAssembler.toModel(authService.me());
    }
}
