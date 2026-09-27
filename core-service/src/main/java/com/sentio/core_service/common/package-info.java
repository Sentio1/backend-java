/**
 * Shared kernel of core-service: soft-delete base entities and the security principal. Every
 * other module may use any of it - it holds no business logic of its own.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.sentio.core_service.common;
