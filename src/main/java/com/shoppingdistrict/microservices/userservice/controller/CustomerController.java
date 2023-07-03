package com.shoppingdistrict.microservices.userservice.controller;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.mail.MessagingException;
import javax.naming.NoPermissionException;
import javax.validation.Valid;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import com.shoppingdistrict.microservices.userservice.EmailService;
import com.shoppingdistrict.microservices.userservice.configuration.Configuration;
import com.shoppingdistrict.microservices.userservice.repository.CustomerRepository;
import com.shoppingdistrict.microservices.userservice.repository.SubscriptionRepository;

import commonModule.ApiResponse;
import commonModule.EnableStatus;
import commonModule.RandomStringGenerator;

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

//	@Autowired
//	private PasswordEncoder passwordEncoder;
	
	@Autowired
	private EmailService emailService;

	@Context
	private SecurityContext sc;
	
	private final String SUBJECT = "Tech District: Please confirm your Email Address";
	private final String PATH_USER_CODE_URL = "usercode";
	private final String PATH_SUBSCRIPTION_CODE_URL = "subcode";
	
	@Value("${url.confirmpage}")
	private String urlEmailVerPage;
	

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
		
		
		logger.debug("Is Customer email verified ? {} and enabled status {}",customer.get(0).isEmailVerified(), customer.get(0).getEnabled());

		if (customer.size() > 0) {
			if(customer.get(0).isEmailVerified() && customer.get(0).getEnabled() == EnableStatus.ACTIVE.getValue() ) {
				logger.info("Existing from getUserCredential, only return first customer if exist");
				return customer.get(0).getUsername() + "," + customer.get(0).getPassword() + "," + customer.get(0).getEmail();
			} else {
				logger.info("User hasn't verify email");
				return null;
			}	
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
	public ResponseEntity<Object> createCustomer(@Valid @RequestBody Users customer) throws UnsupportedEncodingException, MessagingException {
		logger.info("Entry to createCustomer");

		logger.info("New user to be created {} and has accepted terms and conditions {}", customer.getUsername(), customer.isAcceptTermsConditions());

//		String encodedPassword = passwordEncoder.encode(customer.getPassword());
//		customer.setPassword(encodedPassword);
		customer.setEmailConfirmCode(RandomStringGenerator.generateRandomString(7));
		
		customer.setPassword( RandomStringGenerator.encodeString(customer.getPassword().length(),customer.getPassword()) );
		

		Users savedCustomer = repository.saveAndFlush(customer);
		
		//send mail
		String recipient = savedCustomer.getEmail();
		String content = createMailContent(savedCustomer, null);
		emailService.sendMails(recipient, SUBJECT, content);
		
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(savedCustomer.getId()).toUri();

		logger.info("Returning newly created customer id {} {} and exiting from createCustomer", savedCustomer.getId(),
				savedCustomer);

		return ResponseEntity.created(location).build();

	}
	
	
	
	@PostMapping("/customers/emailconfirmation")
	public ResponseEntity<ApiResponse> confirmUserEmail(@Valid @RequestBody Users customer) {
		logger.info("Entry to confirmUserEmail");

		logger.info("User {} to confirm for email confirmation code {}", customer.getUsername(), customer.getEmailConfirmCode());
		
		List<Users> existingCustomer = repository.findByUsername(customer.getUsername());
		ApiResponse response;
		
		if (existingCustomer.size() > 0) {
			if (existingCustomer.get(0).getEmailConfirmCode().equalsIgnoreCase(customer.getEmailConfirmCode())) {
				
				if (existingCustomer.get(0).isEmailVerified() ) {
					response = new ApiResponse("Email confirmation code is invalid.", null);
					logger.info("Email is already verified for user {}", 
							customer.getUsername());
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
				}
				
				existingCustomer.get(0).setEmailVerified(true);
				existingCustomer.get(0).setEnabled(EnableStatus.ACTIVE.getValue());
				repository.saveAndFlush(existingCustomer.get(0));
				
				response = new ApiResponse("Great. You can now login to your account.", null);
				logger.info("Email confirmation is successful and exiting from confirmUserEmail", 
						customer.getUsername());
				return ResponseEntity.status(HttpStatus.CREATED).body(response);
			} else {
				response = new ApiResponse("Email confirmation code is invalid.", null);
				logger.info("Email confirmation code is invalid and exiting from confirmUserEmail", 
						customer.getUsername());
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}
		} else {
			response = new ApiResponse("User with given user name not found.", null);
			logger.info("User with given username {} not found and exiting from confirmUserEmail", 
					customer.getUsername());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

	}
	

	@PostMapping("/subscription")
	public ResponseEntity<ApiResponse> createSubscription(@Valid @RequestBody Subscription subscription) throws UnsupportedEncodingException, MessagingException{
		logger.info("Entry to createSubscription");

		logger.info("Email subscription to be created for email {} and has accepted terms and conditions {}", subscription.getEmail(), subscription.isAcceptTermsConditions());
		subscription.setEmailConfirmCode(RandomStringGenerator.generateRandomString(7));
		Subscription savedSubscription = subscriptionRepository.saveAndFlush(subscription);
		
		//send mail
		String recipient = savedSubscription.getEmail();
		String content = createMailContent(null, savedSubscription);
		emailService.sendMails(recipient, SUBJECT, content);
		
		ApiResponse response = new ApiResponse("Great. We have sent the link to your email for verification. Please check your email inbox, including spam and junk folders. ", null);
		
		logger.info("Returning newly created subscription for user{}, email{} and exiting from createSubscription", 
				savedSubscription.getFirstname(), savedSubscription.getEmail());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);

	}
	
	@PostMapping("/subscription/emailconfirmation")
	public ResponseEntity<ApiResponse> confirmSubscriptionEmail(@Valid @RequestBody Subscription sub) {
		logger.info("Entry to confirmSubscriptionEmail");

		logger.info("Email address {} to confirm for email confirmation code {}", sub.getEmail(), sub.getEmailConfirmCode());
		
		List<Subscription> subscription = subscriptionRepository.findByEmail(sub.getEmail());
		ApiResponse response;
		
		if (subscription.size() > 0) {
			if (subscription.get(0).getEmailConfirmCode().equalsIgnoreCase(sub.getEmailConfirmCode())) {
				
				if (subscription.get(0).isEmailVerified() ) {
					response = new ApiResponse("Email confirmation code is invalid.", null);
					logger.info("Email is already verified for email {}", 
							sub.getEmail());
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
				}
				
				subscription.get(0).setEmailVerified(true);
				subscription.get(0).setEnabled(EnableStatus.ACTIVE.getValue());
				subscriptionRepository.saveAndFlush(subscription.get(0));
				
				response = new ApiResponse("Great. You will start receiving our news letters", null);
				logger.info("Email confirmation is successful and exiting from confirmSubscriptionEmail {}", 
						sub.getEmail());
				return ResponseEntity.status(HttpStatus.CREATED).body(response);
			} else {
				response = new ApiResponse("Email confirmation code is invalid.", null);
				logger.info("Email confirmation code is invalid and exiting from confirmSubscriptionEmail {}", 
						sub.getEmail());
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}
		} else {
			response = new ApiResponse("Subscription with given email address not found.", null);
			logger.info("Subscription with given email address {} not found and exiting from confirmSubscriptionEmail", 
					sub.getEmail());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

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
	
	private String createMailContent(Users savedCustomer, Subscription savedSubscription) {
		StringBuilder builder = new StringBuilder();
		
		
		if (savedCustomer != null) {
			builder.append("Dear "+savedCustomer.getFirstname()+",").append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append("Thank you for joining us. Please follow the provided link to verify your email address by entering your username/email and the verification code.").append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append("Link: "+ urlEmailVerPage+"/"+PATH_USER_CODE_URL).append(System.lineSeparator());
			builder.append("User Name: " + savedCustomer.getUsername()).append(System.lineSeparator());
			builder.append("Email Confirmation Code: " + savedCustomer.getEmailConfirmCode()).append(System.lineSeparator());
		} 
		
		if (savedSubscription != null) {
			builder.append("Dear "+savedSubscription.getFirstname()+",").append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append("Thank you for joining us. Please follow the provided link to verify your email address by entering your username/email and the verification code.").append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append("Link: "+ urlEmailVerPage+"/"+PATH_SUBSCRIPTION_CODE_URL).append(System.lineSeparator());
			builder.append("Email: " + savedSubscription.getEmail()).append(System.lineSeparator());
			builder.append("Email Confirmation Code: " + savedSubscription.getEmailConfirmCode()).append(System.lineSeparator());
		}
		builder.append(System.lineSeparator());
		builder.append("Thank you, and we are excited to have you as part of our user community.").append(System.lineSeparator());
		builder.append(System.lineSeparator());
		builder.append("Warm Regard,").append(System.lineSeparator());
		builder.append("Tech District Team").append(System.lineSeparator());
		
		return builder.toString();
	}

}
