package com.cmacgm.gbs.rst.api.timesheet.config;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Registers Daily and Monthly Timesheet jobs from application configuration.
 */
@Component
@ConditionalOnProperty(prefix = "timesheet.sync.schedule", name = "enabled", havingValue = "true")
public class TimesheetQuartzScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimesheetQuartzScheduler.class);
    private static final String GROUP = "timesheet-sync";

    private final Scheduler scheduler;
    private final TimesheetSyncProperties properties;
    private final ApplicationContext applicationContext;

    /**
     * @param scheduler Quartz
     * @param properties system cron rules
     * @param applicationContext used by TimesheetSyncJob
     */
    public TimesheetQuartzScheduler(
            Scheduler scheduler, TimesheetSyncProperties properties, ApplicationContext applicationContext) {
        this.scheduler = scheduler;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    /**
     * Registers Daily and Monthly triggers from configuration.
     */
    @PostConstruct
    public void apply() {
        try {
            scheduler.getContext().put("applicationContext", applicationContext);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to bind Timesheet Quartz context", ex);
        }
        TimesheetSyncProperties.Schedule schedule = properties.getSchedule();
        register("DAILY", schedule.getDailyCron());
        register("MONTHLY", schedule.getMonthlyCron());
    }

    private void register(String kind, String cronExpression) {
        JobKey jobKey = JobKey.jobKey(kind, GROUP);
        try {
            scheduler.deleteJob(jobKey);
            if (cronExpression == null || cronExpression.isBlank()) {
                log.info("Timesheet Quartz {} skipped: cron is blank", kind);
                return;
            }
            JobDetail job = JobBuilder.newJob(TimesheetSyncJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("kind", kind)
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TriggerKey.triggerKey(kind, GROUP))
                    .forJob(job)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression.trim()))
                    .build();
            scheduler.scheduleJob(job, trigger);
            log.info("Timesheet Quartz {} cron={}", kind, cronExpression);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to schedule Timesheet " + kind, ex);
        }
    }
}
