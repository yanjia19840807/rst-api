package com.cmacgm.gbs.rst.api;

import com.cmacgm.gbs.rst.api.forecast.ForecastProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ForecastProperties.class)
public class RstApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RstApiApplication.class, args);
	}

}
