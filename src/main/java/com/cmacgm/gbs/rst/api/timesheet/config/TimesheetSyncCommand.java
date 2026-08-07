package com.cmacgm.gbs.rst.api.timesheet.config;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncService;

/**
 * One-shot CLI entry that loads a Timesheet Excel snapshot and exits.
 *
 * <p>Example:
 * {@code ./mvnw spring-boot:run
 * -Dspring-boot.run.arguments=--timesheet.sync.enabled=true
 * --timesheet.sync.date=2026-06-30 --server.port=0}
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "timesheet.sync", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TimesheetSyncProperties.class)
public class TimesheetSyncCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSyncCommand.class);

    private final TimesheetSyncService syncService;
    private final TimesheetSyncProperties properties;
    private final ConfigurableApplicationContext context;
    private final Clock clock;

    /**
     * @param syncService sync application service
     * @param properties sync CLI properties
     * @param context application context used for clean exit
     * @param clock clock for default sync date
     */
    public TimesheetSyncCommand(
            TimesheetSyncService syncService,
            TimesheetSyncProperties properties,
            ConfigurableApplicationContext context,
            Clock clock) {
        this.syncService = syncService;
        this.properties = properties;
        this.context = context;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate syncDate = properties.getDate() != null
                ? properties.getDate()
                : LocalDate.now(clock);
        try {
            TimesheetSyncService.SyncResult result = syncService.sync(
                    properties.getFile(),
                    properties.getSheet(),
                    syncDate);
            log.info(
                    "Timesheet sync completed: id={} status={} rows={} syncDate={} hash={}",
                    result.id(),
                    result.status(),
                    result.rowCount(),
                    result.syncDate(),
                    result.dataHash());
            System.exit(SpringApplication.exit(context, () -> 0));
        } catch (Exception ex) {
            log.error("Timesheet sync failed: {}", ex.getMessage(), ex);
            System.exit(SpringApplication.exit(context, () -> 1));
        }
    }
}
