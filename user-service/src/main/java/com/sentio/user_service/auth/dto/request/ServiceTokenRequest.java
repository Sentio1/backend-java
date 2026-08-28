package com.sentio.user_service.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

// clientId = email службового User (auth.users, platform_role = SERVICE) - той самий
// унікальний ключ, яким і так шукають юзера всюди (UserRepository.findByEmail),
// без окремої client_id-колонки під один поки-що акаунт.
public record ServiceTokenRequest(@NotBlank String clientId, @NotBlank String secret) {}
