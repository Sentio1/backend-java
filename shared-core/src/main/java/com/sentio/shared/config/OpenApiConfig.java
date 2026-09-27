//package com.sentio.shared.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class OpenApiConfig {
//
//    public static final String ACCESS_COOKIE_SCHEME = "cookieAccessToken";
//    public static final String REFRESH_COOKIE_SCHEME = "cookieRefreshToken";
//    public static final String OPEN_API_VERSION = "0.0.1";
//
//    @Bean
//    public OpenAPI openAPI() {
//        return new OpenAPI()
//                .info(new Info()
//                        .title("MatchPoint API")
//                        .description("REST API documentation for MatchPoint platform")
//                        .version(OPEN_API_VERSION)
//                        .contact(new Contact().name("MatchPoint Team")))
//                .addSecurityItem(new SecurityRequirement()
//                        .addList(ACCESS_COOKIE_SCHEME)
//                        .addList(REFRESH_COOKIE_SCHEME))
//                .components(new Components()
//                        .addSecuritySchemes(ACCESS_COOKIE_SCHEME, new SecurityScheme()
//                                .name("access_token")
//                                .type(SecurityScheme.Type.HTTP)
//                                .in(SecurityScheme.In.COOKIE)
//                                .description("HttpOnly access token cookie"))
//                        .addSecuritySchemes(REFRESH_COOKIE_SCHEME, new SecurityScheme()
//                                .name("refresh_token")
//                                .type(SecurityScheme.Type.HTTP)
//                                .in(SecurityScheme.In.COOKIE)
//                                .description("HttpOnly refresh token cookie")));
//    }
//}
