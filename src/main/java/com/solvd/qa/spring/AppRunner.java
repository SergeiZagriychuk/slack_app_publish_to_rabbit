package com.solvd.qa.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan
public class AppRunner {

	public static void main(String[] args) {
		SpringApplication.run(AppRunner.class, args);
	}
}