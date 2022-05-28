package com.astralbrands.orders.config;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfiguration {
	
	@Bean(name = "x3DataSource")
	public DataSource x3DataSource() {
		DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
		dataSourceBuilder.url("jdbc:sqlserver://AB-SAGEDB-01\\X3:1433;DatabaseName=x3;user=bitBoot;password=pluJVT8IEGG");
		return dataSourceBuilder.build();
	}

}
