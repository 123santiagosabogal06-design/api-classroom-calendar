package com.apiclassroom.api_classroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiClassroomApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiClassroomApplication.class, args);
	}

}
