package com.cmacgm.gbs.rst.api.timesheet.config;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncService;

/**
 * Quartz job that runs SharePoint Timesheet sync for one kind.
 */
@DisallowConcurrentExecution
public class TimesheetSyncJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSyncJob.class);

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String kind = context.getMergedJobDataMap().getString("kind");
        try {
            ApplicationContext app = (ApplicationContext) context.getScheduler().getContext().get("applicationContext");
            TimesheetSyncService syncService = app.getBean(TimesheetSyncService.class);
            log.info("Timesheet Quartz sync starting: kind={}", kind);
            syncService.syncFromSharePoint(kind, "SYSTEM");
        } catch (Exception ex) {
            log.error("Timesheet Quartz sync failed: kind={} {}", kind, ex.getMessage(), ex);
            throw new JobExecutionException(ex);
        }
    }
}
