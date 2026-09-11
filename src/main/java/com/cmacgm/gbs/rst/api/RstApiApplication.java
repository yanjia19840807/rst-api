package com.cmacgm.gbs.rst.api;

import com.cmacgm.gbs.rst.api.forecast.ForecastProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.mail.application.MailProperties;
import com.cmacgm.gbs.rst.api.process.ProcessProperties;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSharePointProperties;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSyncProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		ForecastProperties.class,
		MicrosoftGraphProperties.class,
		TimesheetSharePointProperties.class,
		ProcessProperties.class,
		TimesheetSyncProperties.class,
		MailProperties.class
})
public class RstApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RstApiApplication.class, args);
	}

}
