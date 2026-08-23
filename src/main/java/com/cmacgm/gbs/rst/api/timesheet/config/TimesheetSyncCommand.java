package com.cmacgm.gbs.rst.api.timesheet.config;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncService;

/**
 * One-shot CLI entry that loads Daily and/or Monthly Timesheet files and exits.
 *
 * <p>Example:
 * {@code ./mvnw spring-boot:run
 * -Dspring-boot.run.arguments=--timesheet.sync.enabled=true
 * --timesheet.sync.kind=all --timesheet.sync.source=file --server.port=0}
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "timesheet.sync", name = "enabled", havingValue = "true")
public class TimesheetSyncCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSyncCommand.class);

    private final TimesheetSyncService syncService;
    private final TimesheetSyncProperties properties;
    private final ConfigurableApplicationContext context;

    /**
     * @param syncService sync application service
     * @param properties sync CLI properties
     * @param context application context used for clean exit
     */
    public TimesheetSyncCommand(
            TimesheetSyncService syncService,
            TimesheetSyncProperties properties,
            ConfigurableApplicationContext context) {
        this.syncService = syncService;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String kind = properties.getKind() == null
                ? "all"
                : properties.getKind().trim().toLowerCase(Locale.ROOT);
        try {
            List<TimesheetSyncService.SyncResult> results = switch (kind) {
                case "daily" -> List.of(syncService.sync("DAILY"));
                case "monthly" -> List.of(syncService.sync("MONTHLY"));
                default -> syncService.syncAll();
            };
            for (TimesheetSyncService.SyncResult result : results) {
                log.info(
                        "Timesheet sync completed: id={} kind={} status={} rows={} syncDate={} hash={}",
                        result.id(),
                        result.kind(),
                        result.status(),
                        result.rowCount(),
                        result.syncDate(),
                        result.dataHash());
            }
            System.exit(SpringApplication.exit(context, () -> 0));
        } catch (Exception ex) {
            log.error("Timesheet sync failed: {}", ex.getMessage(), ex);
            System.exit(SpringApplication.exit(context, () -> 1));
        }
    }
}
