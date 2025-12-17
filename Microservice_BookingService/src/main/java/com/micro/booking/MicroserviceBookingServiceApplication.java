package com.micro.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class MicroserviceBookingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MicroserviceBookingServiceApplication.class, args);
	}

}
