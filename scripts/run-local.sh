#!/bin/sh
# Simulated login on the uat profile. Dev Identity only; Azure SSO stays off.
# The uat profile keeps SharePoint Timesheet sync on and reads 4.RST/2.UAT.
cd "$(dirname "$0")/.." || exit 1

export SPRING_PROFILES_ACTIVE=uat
export APP_SECURITY_DEV_IDENTITY_OVERRIDE_ENABLED=true

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=uat
