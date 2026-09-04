package com.cmacgm.gbs.rst.api;

import com.cmacgm.gbs.rst.api.forecast.ForecastProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.graph.RstSharePointProperties;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetProcessProperties;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSyncProperties;
import com.cmacgm.gbs.rst.api.mail.application.RstMailProperties;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		ForecastProperties.class,
		MicrosoftGraphProperties.class,
		RstSharePointProperties.class,
		TimesheetProcessProperties.class,
		TimesheetSyncProperties.class,
		RstMailProperties.class,
		WorkflowProperties.class
})
public class RstApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RstApiApplication.class, args);
	}

}
