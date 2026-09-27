// Domain events identity publishes for other modules (e.g. notification) to react to. Exposed as a
// named interface so listeners outside identity can depend on these types and nothing else.
@org.springframework.modulith.NamedInterface("events")
package com.sentio.user_service.identity.event;
