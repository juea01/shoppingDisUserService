package com.shoppingdistrict.microservices.userservice.controller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.naming.NoPermissionException;
import javax.validation.Valid;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.shoppingdistrict.microservices.model.model.Orders;
import com.shoppingdistrict.microservices.model.model.Subscription;
import com.shoppingdistrict.microservices.model.model.Users;
import com.shoppingdistrict.microservices.userservice.configuration.Configuration;
import com.shoppingdistrict.microservices.userservice.repository.CustomerRepository;
import com.shoppingdistrict.microservices.userservice.repository.SubscriptionRepository;

import commonModule.ApiResponse;

@RestController
@RequestMapping("/user-service")
public class CustomerController {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private Environment environment;

	@Autowired
	private CustomerRepository repository;
	
	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private Configuration configuration;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Context
	private SecurityContext sc;

	/**
	 * Note: From keycloak server authentication.name is giving f:63770d65-e3a9-4c3c-9e4b-b765c6b255f5:smith
	 * and therefore string manipulation on Spring Expression of PostAuthorize
	 * @param username
	 * @return
	 * @throws NoPermissionException
	 */
	@GetMapping("/customers/{username}")
	@PreAuthorize("#username.toUpperCase() == authentication.name.substring(authentication.name.indexOf(\":\", 3)+1).toUpperCase()")
	public Users retrieveCustomerByName(@PathVariable String username) throws NoPermissionException {
		logger.info("Entry to retrieveCustomerByName {}", username);
		List<Users> customer = repository.findByUsername(username);
		Users dtoUser = new Users();
		if (customer.size()>0) {
			dtoUser.setId(customer.get(0).getId());
			dtoUser.setFirstname(customer.get(0).getFirstname());
			dtoUser.setLastname(customer.get(0).getLastname());
			
			dtoUser.setUsername(customer.get(0).getUsername());
			dtoUser.setEmail(customer.get(0).getEmail());
			
			dtoUser.setCity(customer.get(0).getCity());
			dtoUser.setCountry(customer.get(0).getCountry());
			
			dtoUser.setOccupation(customer.get(0).getOccupation());
			dtoUser.setGender(customer.get(0).getGender());
		}
		
		logger.info("Existing from retrieveCustomerByName, Number of coustomer found", customer.size());
		return dtoUser;
	}
	

	private String getLoggedInUserName() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Jwt jwt = (Jwt) authentication.getPrincipal();
		String name = (String) jwt.getClaims().get("preferred_username");
		return name;

	}

	/**
	 * TODO: Currently this method is not secured and only being called from key cloak oauth2 server.
	 * Need to fix so that key cloak can call database directly or need to secure this method (may be java built in mechanism)
	 * if this API approach is going to be continued
	 * @param username
	 * @return
	 */
	@GetMapping("/credential/{username}")
	public String getUserCredential(@PathVariable String username) {
		logger.info("Entry to getUserCredential, user name {}", username);

		List<Users> customer = repository.findByUsername(username);

		logger.info("Number of found customer with the user name {}", customer.size());
		logger.info("Existing from getUserCredential, only return first customer if exist");

		if (customer.size() > 0) {
			return customer.get(0).getUsername() + "," + customer.get(0).getPassword();
		} else {
			return null;
		}

	}
	
	@GetMapping("/customers/exist/username/{username}")
	public boolean chekcIfUserNameExist(@PathVariable String username) throws NoPermissionException {
		logger.info("Entry to chekcIfUserNameExist {}", username);
		boolean userNameExist = false;
		List<Users> customer = repository.findByUsername(username);
		if (customer.size() > 0) {
			userNameExist = true;
		}
		logger.info("Existing from chekcIfUserNameExist, Number of coustomer found with given user name {}", customer.size());
		return userNameExist;
	}
	
	@GetMapping("/customers/exist/email/{email}")
	public boolean chekcIfUserEmailExist(@PathVariable String email) throws NoPermissionException {
		logger.info("Entry to chekcIfUserEmailExist {}", email);
		boolean userEmailExist = false;
		List<Users> customer = repository.findByEmail(email);
		if (customer.size() > 0) {
			userEmailExist = true;
		}
		logger.info("Existing from chekcIfUserEmailExist, Number of coustomer found with given user email {}", customer.size());
		return userEmailExist;
	}
	

	@PostMapping("/customers")
	public ResponseEntity<Object> createCustomer(@Valid @RequestBody Users customer) {
		logger.info("Entry to createCustomer");

		logger.info("New user to be created {} and has accepted terms and conditions {}", customer.getUsername(), customer.isAcceptTermsConditions());

//		String encodedPassword = passwordEncoder.encode(customer.getPassword());
//		customer.setPassword(encodedPassword);

		Users savedCustomer = repository.saveAndFlush(customer);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(savedCustomer.getId()).toUri();

		logger.info("Returning newly created customer id {} {} and exiting from createCustomer", savedCustomer.getId(),
				savedCustomer);

		return ResponseEntity.created(location).build();

	}
	

	@PostMapping("/subscription")
	public ResponseEntity<ApiResponse> createSubscription(@Valid @RequestBody Subscription subscription) {
		logger.info("Entry to createSubscription");

		logger.info("Email subscription to be created for email {} and has accepted terms and conditions {}", subscription.getEmail(), subscription.isAcceptTermsConditions());
		Subscription savedSubscription = subscriptionRepository.saveAndFlush(subscription);
		
		ApiResponse response = new ApiResponse("Great. We have sent the link to your email for verification. Please check your email inbox, including spam and junk folders. ", null);
		
		logger.info("Returning newly created subscription for user{}, email{} and exiting from createSubscription", 
				savedSubscription.getFirstname(), savedSubscription.getEmail());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);

	}
	
	@GetMapping("/subscription/email/{email}")
	public boolean chekcIfSubscriptionemailExist(@PathVariable String email) throws NoPermissionException {
		logger.info("Entry to chekcIfSubscriptionemailExist {}", email);
		boolean userEmailExist = false;
		List<Subscription> subsriptions = subscriptionRepository.findByEmail(email);
		if (subsriptions.size() > 0) {
			userEmailExist = true;
		}
		logger.info("Existing from chekcIfSubscriptionemailExist, Number of subscription found with given email {}", subsriptions.size());
		return userEmailExist;
	}
	
	
	
	
	//TODO: Shall try and catch here or let error handling component to handle
	@PutMapping("/customers/{id}")
	@PreAuthorize("#customer.getUsername().toUpperCase() == authentication.name.substring(authentication.name.indexOf(\":\", 3)+1).toUpperCase()")
	public ResponseEntity<Object> updateCustomer(@Valid @RequestBody Users customer, @PathVariable Integer id) {
		logger.info("Entry to updateCustomer");

		logger.info("User to be updated {}", customer.getUsername());

//		String encodedPassword = passwordEncoder.encode(customer.getPassword());
//		customer.setPassword(encodedPassword);
		Optional<Users> existingUser = repository.findById(id);
		existingUser.get().setFirstname(customer.getFirstname());
		existingUser.get().setLastname(customer.getLastname());
		
		existingUser.get().setUsername(customer.getUsername());
		existingUser.get().setEmail(customer.getEmail());
		
		existingUser.get().setCity(customer.getCity());
		existingUser.get().setCountry(customer.getCountry());
		
		
		existingUser.get().setOccupation(customer.getOccupation());
		existingUser.get().setGender(customer.getGender());
		
		Users updatedCustomer = repository.saveAndFlush(existingUser.get());
		

		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(updatedCustomer.getId()).toUri();

		logger.info("Returning newly updated customer id {} {} and exiting from updateCustomer", updatedCustomer.getId(),
				updatedCustomer);

		return ResponseEntity.created(location).build();

	}

	// retrieveOrder
	@GetMapping("/customers/{username}/orders")
	@PreAuthorize("#username.toUpperCase() == authentication.name.substring(authentication.name.indexOf(\":\", 3)+1).toUpperCase()")
	public List<Orders> retrieveAllOrdersOfCustomer(@PathVariable String username) {
		logger.info("Entry to retrieveAllOrdersOfCustomer");

		List<Users> user = repository.findByUsername(username);
		if (user.size() < 1) {
			throw new NoSuchElementException("user name" + username);
		}

		logger.info("Returning orders of user name- {} and exiting from retrieveAllOrdersOfCustomer",
				username);

		return  user.get(0).getOrders();

	}

}
