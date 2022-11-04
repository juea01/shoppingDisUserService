package com.shoppingdistrict.microservices.userservice;

import java.math.BigDecimal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(
		{"com.shoppingdistrict.microservices.exceptionshandling",
		"com.shoppingdistrict.microservices.userservice","com.shoppingdistrict.microservices.userservice.configuration"})
@EntityScan(basePackages="com.shoppingdistrict.microservices.model.model")
public class UserserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserserviceApplication.class, args);
	}

}
