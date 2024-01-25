package com.shoppingdistrict.microservices.userservice.controller;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

import com.shoppingdistrict.microservices.model.model.Orders;
import com.shoppingdistrict.microservices.model.model.PaidSubscription;
import com.shoppingdistrict.microservices.model.model.Subject;
import com.shoppingdistrict.microservices.model.model.Subscription;
import com.shoppingdistrict.microservices.model.model.Users;
import com.shoppingdistrict.microservices.userservice.EmailService;
import com.shoppingdistrict.microservices.userservice.configuration.Configuration;
import com.shoppingdistrict.microservices.userservice.repository.CustomerRepository;
import com.shoppingdistrict.microservices.userservice.repository.PaidSubscriptionRepository;
import com.shoppingdistrict.microservices.userservice.repository.SubscriptionRepository;

import commonModule.ApiResponse;
import commonModule.EnableStatus;
import commonModule.RandomStringGenerator;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.json.JSONObject;

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
	private PaidSubscriptionRepository paidSubsRepo;

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
	
	@Value("${stripe.secretkey}")
	private String stripeSecretKey;
	
	@Value("${stripe.paymentsuccess.url}")
	private String stripePaymentSuccessUrl;
	
	@Value("${stripe.endpointsecret}")
	private String stripeEndPointKey;

	private Map<String, String> sessionUserNamStore = new HashMap<>();
	private Object storeLock = new Object();
	
	@Value("${keycloak.management.id}")
	private String keycloakManagementClientId;
	
	@Value("${keycloak.management.secret}")
	private String keycloakManagementClientSecret;
	
	@Value("${keycloak.token.url}")
	private String keycloakManagementTokenUrl;
	
	@Value("${keycloak.user.evict.url}")
	private String keycloakUserEvictUrl;
	
	@Value("${keycloak.management.scope}")
	private String keycloakManagementScope;
	
	@Value("${keycloak.management.grant_type}")
	private String keycloakManagementGrantType;
	

	/**
	 * Note: From keycloak server authentication.name is giving
	 * f:63770d65-e3a9-4c3c-9e4b-b765c6b255f5:smith and therefore string
	 * manipulation on Spring Expression of PostAuthorize
	 * 
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
		if (customer.size() > 0) {
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

		logger.info("Exiting from retrieveCustomerByName, Number of coustomer found", customer.size());
		return dtoUser;
	}

	private String getLoggedInUserName() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Jwt jwt = (Jwt) authentication.getPrincipal();
		String name = (String) jwt.getClaims().get("preferred_username");
		return name;

	}
	

	private String getKeyCloakManagementAccessToken()throws Exception {
		logger.info("Entry to getKeyCloakManagementAccessToken");
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		String requestBody= "client_id="+keycloakManagementClientId+"&client_secret="+keycloakManagementClientSecret
				+"&scope="+keycloakManagementScope+"&grant_type="+keycloakManagementGrantType;
		
		HttpEntity<String> reqEntity = new HttpEntity<>(requestBody, headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> responseEntity = restTemplate.exchange(keycloakManagementTokenUrl, HttpMethod.POST, reqEntity, String.class);
		
		if(responseEntity.getStatusCode().is2xxSuccessful()) {
			logger.info("Token Code received. Exiting from getKeyCloakManagementAccessToken");
			JSONObject jsonObject = new JSONObject(responseEntity.getBody().toString());
			return jsonObject.getString("access_token");
		} else {
			logger.info("Error requesting Token Code {}");
			throw new Exception(" Error requesting Token for management");
		}
	}
	
	private boolean evictUserFromKeycloakCache(String accessToken, String userId)throws Exception {
		logger.info("Entry to evictUserFromKeycloakCache for userId {}", userId);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("Authorization", "Bearer "+ accessToken);
		
		String requestBody= "userId="+userId;
		
		HttpEntity<String> reqEntity = new HttpEntity<>(requestBody, headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> responseEntity = restTemplate.exchange(keycloakUserEvictUrl, HttpMethod.POST, reqEntity, String.class);
		
		if(responseEntity.getStatusCode().is2xxSuccessful()) {
			logger.info("User {} evicted successfully from keycloak cache, exiting from evictUserFromKeycloakCache", userId);
			return true;
		} else {
			logger.info("Error occurred when evicting user from keycloak cache");
			throw new Exception("Error occurred when evicting user from keycloak cache");
		}
	}

	@PostMapping("/subscription/create-checkout-session/{priceId}/{username}/{keycloakUserId}")
	public ResponseEntity<Object> createCheckoutSession(@PathVariable String priceId, @PathVariable String username, @PathVariable String keycloakUserId) {
		logger.info("Entry to createCheckoutSession for priceId {}, username {} and keycloak User Id {}", priceId, username, keycloakUserId);
		// Need to use secret key for api-key not Publishable key
		Stripe.apiKey = stripeSecretKey;
		String YOUR_DOMAIN = stripePaymentSuccessUrl;

		try {
			SessionCreateParams params = new SessionCreateParams.Builder()
					.setMode(SessionCreateParams.Mode.SUBSCRIPTION)
					.setSuccessUrl(YOUR_DOMAIN)
					// .setCancelUrl(domainUrl + "/canceled.html")
					.addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L)
							// Provide the exact Price ID (for example, pr_1234) of the product you want to
							// sell
							.setPrice(priceId).build())
					.build();

			Session session = Session.create(params);

			synchronized (storeLock) {
				logger.info("Storing created session id and username pair in temp key store {} {}", session.getId(),
						username);
				sessionUserNamStore.put(session.getId(), username+","+keycloakUserId);
			}

			RedirectView redirectView = new RedirectView(session.getUrl());
			redirectView.setStatusCode(HttpStatus.SEE_OTHER);
			logger.info("Exiting from createCheckoutSession for priceId {}", priceId);
			return new ResponseEntity<>(redirectView, HttpStatus.OK);
		} catch (Exception e) {
			Map<String, Object> messageData = new HashMap<>();
			logger.error("Error occurred in createCheckoutSession for priceId {} and error is {}", priceId, e);
			messageData.put("message", e.getMessage());
			Map<String, Object> responseData = new HashMap<>();
			responseData.put("error", messageData);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

		}

	}
	

	@PostMapping("/subscription/webhook")
	public String provisionSubscription(@RequestBody String payload,
			@RequestHeader("Stripe-Signature") String sigHeader) throws Exception {
		logger.info("Entry to provisionSubscription {}", payload);
		Stripe.apiKey = stripeSecretKey;
		String endpointSecret = stripeEndPointKey;
		Event event = null;
		try {
			event = Webhook.constructEvent(payload, sigHeader, endpointSecret);

			switch (event.getType()) {
			case "checkout.session.completed":
				JSONObject jsonObject = new JSONObject(payload);

				if (jsonObject.getJSONObject("data") != null
						&& jsonObject.getJSONObject("data").getJSONObject("object") != null) {
					JSONObject object = jsonObject.getJSONObject("data").getJSONObject("object");
					String sessionId = object.getString("id");
					String stripeCustomerId = object.getString("customer");

					JSONObject customerDetObject = object.getJSONObject("customer_details");
					if (customerDetObject != null) {
						String paymentUserEmail = customerDetObject.getString("email");
						String paymentUserName = customerDetObject.getString("name");
						
						logger.info("Name {} and email {} used for successful payment for session id {}", paymentUserName,
								paymentUserEmail, sessionId);
						if (!updateCustomerRoleByEmail(paymentUserEmail, "PREMIUM")) {
							logger.warn("Updating customer role with given  email is failing {}", paymentUserEmail);
							logger.warn("User might have provided different email for payment on stripe");

							// Using String Builder here is not necessary
							String[] userDetail = null;
							synchronized (storeLock) {
								logger.info(
										"Retrieving username from temp key store for session id  {} completed event",
										sessionId);
								//userDetail should have {username, keycloak user id}
								userDetail = sessionUserNamStore.get(sessionId).split(",");

								if (userDetail != null) {
									logger.info("Trying to update user role with username {} from session id {}",
											userDetail[0], sessionId);
									evictUserFromKeycloakCache(getKeyCloakManagementAccessToken(), userDetail[1]);
									sessionUserNamStore.remove(sessionId);
									updateCustomerRoleByUserName(userDetail[0], "PREMIUM");
									storeStripeCustomerInfo(stripeCustomerId, repository.findByUsername(userDetail[0]).get(0),  null);
								} else {
									logger.warn("Having issue updating user role with stripe provided email {}",
											paymentUserEmail);
								}
							}

						} else {
							//userDetail should have {username, keycloak user id}
							String[] userDetail = sessionUserNamStore.get(sessionId).split(",");
							evictUserFromKeycloakCache(getKeyCloakManagementAccessToken(), userDetail[1]);
							synchronized (storeLock) {
							 sessionUserNamStore.remove(sessionId);	
							}
							storeStripeCustomerInfo(stripeCustomerId, repository.findByUsername(userDetail[0]).get(0),  null);
						}
					} else {
						logger.info("No customer detail object is present in payload");
					}

				} else {
					logger.info("No JSON data or JSON object is present in payload");
				}
			default:

			}

		} catch (SignatureVerificationException e) {
			// Invalid signature
			logger.error("Error occurred in provisionSubscription {}", e);
			return "";
		}

		logger.info("Exiting from provisionSubscription");
		return "";

	}
	
	
	private void storeStripeCustomerInfo(String stripeCusId, Users user, String subscriptionType) {
		logger.info("Entry to storeStripeCustomerInfo for user name {}, stripe customer id {} "
				+ "and subscription type {}", user.getUsername(), stripeCusId, subscriptionType);
		PaidSubscription paidSubscription = new PaidSubscription();
		paidSubscription.setActiveSubscription(true);
		paidSubscription.setSubscriptionType(subscriptionType);
		paidSubscription.setThirdPartyUserId(stripeCusId);
		paidSubscription.setUser(user);
		paidSubscription.setLastPaidDate(new Timestamp(System.currentTimeMillis()));
		paidSubsRepo.saveAndFlush(paidSubscription);
		logger.info("Successfully stored stripe customer id {} and related info in database", stripeCusId);
		
		logger.info("Exiting from storeStripeCustomerInfo for user name {}, stripe customer id {} "
				+ "and subscription type {}", user.getUsername(), stripeCusId, subscriptionType);
	}

	/**
	 * TODO: Currently this method is not secured and only being called from key
	 * cloak oauth2 server. Need to fix so that key cloak can call database directly
	 * or need to secure this method (may be java built in mechanism) if this API
	 * approach is going to be continued
	 * 
	 * @param username
	 * @return
	 */
	@GetMapping("/credential/{username}")
	public String getUserCredential(@PathVariable String username) {
		logger.info("Entry to getUserCredential, user name {}", username);

		List<Users> customer = repository.findByUsername(username);

		logger.info("Number of found customer with the user name {}", customer.size());

		logger.debug("Is Customer email verified ? {} and enabled status {}", customer.get(0).isEmailVerified(),
				customer.get(0).getEnabled());

		if (customer.size() > 0) {
			if (customer.get(0).isEmailVerified() && customer.get(0).getEnabled() == EnableStatus.ACTIVE.getValue()) {
				logger.info("Exiting from getUserCredential, only return first customer if exist, email {}, role {}",customer.get(0).getEmail(),customer.get(0).getRole());
				return customer.get(0).getUsername() + "," + customer.get(0).getPassword() + ","
						+ customer.get(0).getEmail() + "," + customer.get(0).getRole();
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
		logger.info("Exiting from chekcIfUserNameExist, Number of coustomer found with given user name {}",
				customer.size());
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
		logger.info("Exiting from chekcIfUserEmailExist, Number of coustomer found with given user email {}",
				customer.size());
		return userEmailExist;
	}

	private boolean updateCustomerRoleByEmail(String email, String role) {
		logger.info("Entry to updateCustomerRoleByEmail {}", email);
		List<Users> customer = repository.findByEmail(email);
		if (customer.size() > 0) {
			Users existingUser = customer.get(0);
			existingUser.setRole(role);
			repository.saveAndFlush(existingUser);
			logger.info("User Role updated to PREMIUM, exiting from updateCustomerRoleByEmail ", email);
			return true;
		} else {
			logger.info("No user with given email {} found. Exiting from updateCustomerRoleByEmail", email);
		}
		return false;
	}
	
	private boolean updateCustomerRoleByUserName(String username, String role) {
		logger.info("Entry to updateCustomerRoleByUserName {}", username);
		List<Users> customer = repository.findByUsername(username);
		if (customer.size() > 0) {
			Users existingUser = customer.get(0);
			existingUser.setRole(role);
			repository.saveAndFlush(existingUser);
			logger.info("User Role updated to PREMIUM, exiting from updateCustomerRoleByUserName ", username);
			return true;
		} else {
			logger.info("No user with given username {} found. Exiting from updateCustomerRoleByUserName", username);
		}
		return false;
	}

	@PostMapping("/customers")
	public ResponseEntity<Object> createCustomer(@Valid @RequestBody Users customer)
			throws UnsupportedEncodingException, MessagingException {
		logger.info("Entry to createCustomer");

		logger.info("New user to be created {} and has accepted terms and conditions {}", customer.getUsername(),
				customer.isAcceptTermsConditions());

//		String encodedPassword = passwordEncoder.encode(customer.getPassword());
//		customer.setPassword(encodedPassword);
		customer.setEmailConfirmCode(RandomStringGenerator.generateRandomString(7));

		customer.setPassword(
				RandomStringGenerator.encodeString(customer.getPassword().length(), customer.getPassword()));

		Users savedCustomer = repository.saveAndFlush(customer);

		// send mail
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

		logger.info("User {} to confirm for email confirmation code {}", customer.getUsername(),
				customer.getEmailConfirmCode());

		List<Users> existingCustomer = repository.findByUsername(customer.getUsername());
		ApiResponse response;

		if (existingCustomer.size() > 0) {
			if (existingCustomer.get(0).getEmailConfirmCode().equalsIgnoreCase(customer.getEmailConfirmCode())) {

				if (existingCustomer.get(0).isEmailVerified()) {
					response = new ApiResponse("Email confirmation code is invalid.", null);
					logger.info("Email is already verified for user {}", customer.getUsername());
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
	public ResponseEntity<ApiResponse> createSubscription(@Valid @RequestBody Subscription subscription)
			throws UnsupportedEncodingException, MessagingException {
		logger.info("Entry to createSubscription");

		logger.info("Email subscription to be created for email {} and has accepted terms and conditions {}",
				subscription.getEmail(), subscription.isAcceptTermsConditions());
		subscription.setEmailConfirmCode(RandomStringGenerator.generateRandomString(7));
		Subscription savedSubscription = subscriptionRepository.saveAndFlush(subscription);

		// send mail
		String recipient = savedSubscription.getEmail();
		String content = createMailContent(null, savedSubscription);
		emailService.sendMails(recipient, SUBJECT, content);

		ApiResponse response = new ApiResponse(
				"Great. We have sent the link to your email for verification. Please check your email inbox, including spam and junk folders. ",
				null);

		logger.info("Returning newly created subscription for user{}, email{} and exiting from createSubscription",
				savedSubscription.getFirstname(), savedSubscription.getEmail());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);

	}

	@PostMapping("/subscription/emailconfirmation")
	public ResponseEntity<ApiResponse> confirmSubscriptionEmail(@Valid @RequestBody Subscription sub) {
		logger.info("Entry to confirmSubscriptionEmail");

		logger.info("Email address {} to confirm for email confirmation code {}", sub.getEmail(),
				sub.getEmailConfirmCode());

		List<Subscription> subscription = subscriptionRepository.findByEmail(sub.getEmail());
		ApiResponse response;

		if (subscription.size() > 0) {
			if (subscription.get(0).getEmailConfirmCode().equalsIgnoreCase(sub.getEmailConfirmCode())) {

				if (subscription.get(0).isEmailVerified()) {
					response = new ApiResponse("Email confirmation code is invalid.", null);
					logger.info("Email is already verified for email {}", sub.getEmail());
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
		logger.info("Exiting from chekcIfSubscriptionemailExist, Number of subscription found with given email {}",
				subsriptions.size());
		return userEmailExist;
	}

	// TODO: Shall try and catch here or let error handling component to handle
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

		logger.info("Returning newly updated customer id {} {} and exiting from updateCustomer",
				updatedCustomer.getId(), updatedCustomer);

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

		logger.info("Returning orders of user name- {} and exiting from retrieveAllOrdersOfCustomer", username);

		return user.get(0).getOrders();

	}

	private String createMailContent(Users savedCustomer, Subscription savedSubscription) {
		StringBuilder builder = new StringBuilder();

		if (savedCustomer != null) {
			builder.append("Dear " + savedCustomer.getFirstname() + ",").append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append(
					"Thank you for joining us. Please follow the provided link to verify your email address by entering your username/email and the verification code.")
					.append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append("Link: " + urlEmailVerPage + "/" + PATH_USER_CODE_URL).append(System.lineSeparator());
			builder.append("User Name: " + savedCustomer.getUsername()).append(System.lineSeparator());
			builder.append("Email Confirmation Code: " + savedCustomer.getEmailConfirmCode())
					.append(System.lineSeparator());
		}

		if (savedSubscription != null) {
			builder.append("Dear " + savedSubscription.getFirstname() + ",").append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append(
					"Thank you for joining us. Please follow the provided link to verify your email address by entering your username/email and the verification code.")
					.append(System.lineSeparator());
			builder.append(System.lineSeparator());
			builder.append("Link: " + urlEmailVerPage + "/" + PATH_SUBSCRIPTION_CODE_URL)
					.append(System.lineSeparator());
			builder.append("Email: " + savedSubscription.getEmail()).append(System.lineSeparator());
			builder.append("Email Confirmation Code: " + savedSubscription.getEmailConfirmCode())
					.append(System.lineSeparator());
		}
		builder.append(System.lineSeparator());
		builder.append("Thank you, and we are excited to have you as part of our user community.")
				.append(System.lineSeparator());
		builder.append(System.lineSeparator());
		builder.append("Warm Regard,").append(System.lineSeparator());
		builder.append("Tech District Team").append(System.lineSeparator());

		return builder.toString();
	}

}
