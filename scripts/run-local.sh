#!/bin/sh
# Simulated login on the uat profile. Dev Identity only; Azure SSO stays off.
# Sync stays off so this process is not a second UAT worker.
cd "$(dirname "$0")/.." || exit 1

export SPRING_PROFILES_ACTIVE=uat
export APP_SECURITY_DEV_IDENTITY_OVERRIDE_ENABLED=true
export TIMESHEET_SYNC_DAILY_ENABLED=false
export TIMESHEET_SYNC_MONTHLY_ENABLED=false

exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=uat
