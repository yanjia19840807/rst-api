#!/bin/sh
# Local real SSO. Profile uat. Callback is the registered UAT address:
# https://rightsizingtool-uat.cma-cgm.com/api/sso/callback
# Add "127.0.0.1 rightsizingtool-uat.cma-cgm.com" to /etc/hosts on this machine.
# Sync stays off so this process is not a second UAT worker.
cd "$(dirname "$0")/.." || exit 1

export SPRING_PROFILES_ACTIVE=uat
export TIMESHEET_SYNC_DAILY_ENABLED=false
export TIMESHEET_SYNC_MONTHLY_ENABLED=false

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=uat
