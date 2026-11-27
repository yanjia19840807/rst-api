#!/bin/sh
# Local real SSO. Profile uat. Callback is the registered UAT address:
# https://rightsizingtool-uat.cma-cgm.com/api/sso/callback
# Add "127.0.0.1 rightsizingtool-uat.cma-cgm.com" to /etc/hosts on this machine.
# The uat profile keeps SharePoint Timesheet sync on and reads 4.RST/2.UAT.
cd "$(dirname "$0")/.." || exit 1

export SPRING_PROFILES_ACTIVE=uat

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=uat
