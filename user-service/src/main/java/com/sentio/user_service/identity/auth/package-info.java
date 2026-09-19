// Closed module: nothing in auth is exposed. Endpoints that change the caller's session live here
// (SessionChangingController) and call the organization/user APIs - never the other way round.
@org.springframework.modulith.ApplicationModule
package com.sentio.user_service.identity.auth;
