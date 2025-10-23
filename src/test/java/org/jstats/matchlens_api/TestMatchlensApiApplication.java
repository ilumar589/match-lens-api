package org.jstats.matchlens_api;

import org.springframework.boot.SpringApplication;

public class TestMatchlensApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(MatchlensApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
