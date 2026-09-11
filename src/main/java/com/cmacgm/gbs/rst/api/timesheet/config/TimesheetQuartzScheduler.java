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
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Registers Daily and Monthly Timesheet jobs from application configuration.
 */
@Component
public class TimesheetQuartzScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimesheetQuartzScheduler.class);
    private static final String GROUP = "timesheet-sync";

    private final Scheduler scheduler;
    private final TimesheetSyncProperties properties;
    private final ApplicationContext applicationContext;

    /**
     * @param scheduler Quartz
     * @param properties daily / monthly cron rules
     * @param applicationContext used by TimesheetSyncJob
     */
    public TimesheetQuartzScheduler(
            Scheduler scheduler, TimesheetSyncProperties properties, ApplicationContext applicationContext) {
        this.scheduler = scheduler;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    /**
     * Registers enabled Daily and Monthly triggers.
     */
    @PostConstruct
    public void apply() {
        try {
            scheduler.getContext().put("applicationContext", applicationContext);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to bind Timesheet Quartz context", ex);
        }
        register("DAILY", properties.getDaily());
        register("MONTHLY", properties.getMonthly());
    }

    private void register(String kind, TimesheetSyncProperties.Job job) {
        JobKey jobKey = JobKey.jobKey(kind, GROUP);
        try {
            scheduler.deleteJob(jobKey);
            if (job == null || !job.isEnabled()) {
                log.info("Timesheet Quartz {} skipped: disabled", kind);
                return;
            }
            String cronExpression = job.getCron();
            if (cronExpression == null || cronExpression.isBlank()) {
                log.info("Timesheet Quartz {} skipped: cron is blank", kind);
                return;
            }
            JobDetail detail = JobBuilder.newJob(TimesheetSyncJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("kind", kind)
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TriggerKey.triggerKey(kind, GROUP))
                    .forJob(detail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression.trim()))
                    .build();
            scheduler.scheduleJob(detail, trigger);
            log.info("Timesheet Quartz {} cron={}", kind, cronExpression);
        } catch (SchedulerException ex) {
            throw new IllegalStateException("Unable to schedule Timesheet " + kind, ex);
        }
    }
}
