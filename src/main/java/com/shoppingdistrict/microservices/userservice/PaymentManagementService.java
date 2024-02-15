package com.shoppingdistrict.microservices.userservice;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.view.RedirectView;

import com.shoppingdistrict.microservices.model.model.PaidSubscription;
import com.shoppingdistrict.microservices.model.model.Users;
import com.shoppingdistrict.microservices.userservice.repository.CustomerRepository;
import com.shoppingdistrict.microservices.userservice.repository.PaidSubscriptionRepository;
import com.shoppingdistrict.microservices.userservice.repository.SubscriptionRepository;
import com.stripe.Stripe;

@Service
@Transactional
public class PaymentManagementService {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private CustomerRepository repository;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private PaidSubscriptionRepository paidSubsRepo;
	
	@Autowired
	private KeyCloakService keyCloakService;

	@Value("${stripe.secretkey}")
	private String stripeSecretKey;

	@Value("${stripe.paymentsuccess.url}")
	private String stripePaymentSuccessUrl;

	@Transactional
	public void manageSuccessfulPayment(String stripeCustomerId) throws Exception {
		logger.info("Entry to manageSuccessfulPayment for stripe customer id {}", stripeCustomerId);
		
		PaidSubscription paidSubscription = paidSubsRepo.findByThirdPartyUserId(stripeCustomerId);
		if (paidSubscription.isActiveSubscription()) {
			paidSubscription.setLastPaidDate(new Timestamp(System.currentTimeMillis()));
			paidSubsRepo.saveAndFlush(paidSubscription);
			logger.info("Payment date updated and exiting from manageSuccessfulPayment for stripe customer id {}", stripeCustomerId);
		} else {
			//need to update customer role to premium again
		    paidSubscription.getUser().setRole("PREMIUM");
			paidSubscription.setExpiryDate(null);
			paidSubscription.setActiveSubscription(true);
			paidSubscription.setLastPaidDate(new Timestamp(System.currentTimeMillis()));
			paidSubsRepo.saveAndFlush(paidSubscription);
			
			String userId = keyCloakService.getKeycloakUserIdByUsername(paidSubscription.getUser().getUsername(),
					keyCloakService.getKeyCloakManagementAccessToken());
			keyCloakService.evictUserFromKeycloakCache(keyCloakService.getKeyCloakManagementAccessToken(), userId);
			
			logger.info("Payment date, Customer role and active subscription status updated and exiting from manageSuccessfulPayment for stripe customer id {}", stripeCustomerId);
		}
		
	}
	
	@Transactional
	public void updateStripeCustomerInfo(String stripeCusId, long subscriptionEndTime) {
		logger.info("Entry to updateStripeCustomerInfo forstripe customer id {} "
				, stripeCusId);
		PaidSubscription paidSubscription = paidSubsRepo.findByThirdPartyUserId(stripeCusId);
		if (paidSubscription!= null) {
			if (subscriptionEndTime == 0) {
				paidSubscription.setExpiryDate(new Timestamp(System.currentTimeMillis()));	
			} else {
				paidSubscription.setExpiryDate(new Timestamp(subscriptionEndTime));
			}
			paidSubsRepo.saveAndFlush(paidSubscription);
			logger.info("Successfully set subscription end time for stripe customer {} and time is {}", stripeCusId,new Timestamp(subscriptionEndTime > 0 ? subscriptionEndTime : System.currentTimeMillis()) );
		} else {
			logger.info("Probably it is new Stripe Customer that hasn't been stored in DB yet. Session checkout completed event will perrom that action.");
		}
		
		logger.info("Exiting from updateStripeCustomerInfo");
	}
	
	public boolean updateCustomerRoleByEmail(String email, String role) {
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
	
	public boolean updateCustomerRoleByUserName(String username, String role) {
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
	
	public void storeStripeCustomerInfo(String stripeCusId, Users user, String subscriptionType) {
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
	
	public ResponseEntity<Object>  manageBilling(String username) throws Exception {
		logger.info("Entry to manageBilling for username {}", username);
		// Need to use secret key for api-key not Publishable key
		Stripe.apiKey = stripeSecretKey;
		String YOUR_DOMAIN = stripePaymentSuccessUrl;	
		
		try {
			List<Users> customer = repository.findByUsername(username);
			PaidSubscription paidSubscripion = paidSubsRepo.findByUserId(customer.get(0).getId());
			
			if (paidSubscripion==null || paidSubscripion.getThirdPartyUserId().isEmpty()) {
				logger.info("No content found for usename {} and exiting from manageBilling.", username);
				String statusCode = "404";
				return new ResponseEntity<>(statusCode,HttpStatus.OK);
			}
			
			logger.info("Stripe Customer Id for User name {} is {}", username, paidSubscripion.getThirdPartyUserId());
			
			com.stripe.param.billingportal.SessionCreateParams params = new com.stripe.param.billingportal.SessionCreateParams.Builder()
					.setReturnUrl(YOUR_DOMAIN).setCustomer(paidSubscripion.getThirdPartyUserId()).build();
			com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);

			RedirectView redirectView = new RedirectView(session.getUrl());
			redirectView.setStatusCode(HttpStatus.SEE_OTHER);
			logger.info("Exiting from manageBilling for username {}", username);
			return new ResponseEntity<>(redirectView, HttpStatus.OK);
		} catch (Exception e) {
			Map<String, Object> messageData = new HashMap<>();
			logger.error("Unexpected error occurred in manageBilling for username {} and error is {}", username, e);
			
			//Let exceptions handling component handle it
			throw new Exception("Unexpected error occurred in manageBilling for username "+ username);
		}
		
	}
	
}
