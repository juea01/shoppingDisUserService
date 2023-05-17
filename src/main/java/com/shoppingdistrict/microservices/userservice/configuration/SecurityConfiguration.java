package com.shoppingdistrict.microservices.userservice.configuration;

import java.util.Arrays;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

//import com.example.security.securitydemo.config.KeycloakRoleConverter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

	// permit all security call here as security check is doing upstream security
	// component
	// method level security will be present in this component however
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
		authenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());

		http.cors().configurationSource(new CorsConfigurationSource() {

			@Override
			public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
				// TODO Auto-generated method stub
				CorsConfiguration config = new CorsConfiguration();
				config.setAllowedOrigins(
						Arrays.asList( "http://keycloak:8080",
								"http://54.226.69.223", "http://54.226.69.223:80", "http://localhost", "http://localhost:80"));
				// config.setAllowedOrigins(Collections.singletonList("*"));
				config.setAllowedMethods(Collections.singletonList("*"));
				config.setAllowCredentials(true);
				config.setAllowedHeaders(Collections.singletonList("*"));
				config.setMaxAge(3600L);
				return config;
			}
		}).and()//don't need csrf if using JWT token
				
				.authorizeRequests().antMatchers(HttpMethod.GET, "/user-service/customers/exist/**").permitAll().and()
				.authorizeRequests().antMatchers(HttpMethod.GET, "/user-service/customers/**/**").hasAnyRole("ADMIN", "CUSTOMER").and()
				.authorizeRequests().antMatchers(HttpMethod.PUT, "/user-service/customers/**/**").hasAnyRole("ADMIN", "CUSTOMER").and()
				.authorizeRequests().antMatchers(HttpMethod.POST, "/user-service/customers/").permitAll().and()
				.authorizeRequests().antMatchers(HttpMethod.POST, "/user-service/subscription/").permitAll().and()
				.authorizeRequests().antMatchers(HttpMethod.GET, "/user-service/subscription/email/**").permitAll()
				.anyRequest().permitAll()
				.and().csrf().disable() // oauth2 has already taken care of csrf protection
				.oauth2ResourceServer().jwt().jwtAuthenticationConverter(authenticationConverter);

	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
