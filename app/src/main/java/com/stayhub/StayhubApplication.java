package com.stayhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StayhubApplication {

	public static void main(String[] args) {
		SpringApplication.run(StayhubApplication.class, args);
	}

}
