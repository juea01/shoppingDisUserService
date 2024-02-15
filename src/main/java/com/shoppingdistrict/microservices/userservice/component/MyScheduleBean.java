package com.shoppingdistrict.microservices.userservice.component;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shoppingdistrict.microservices.model.model.PaidSubscription;
import com.shoppingdistrict.microservices.model.model.Users;
import com.shoppingdistrict.microservices.userservice.EmailService;
import com.shoppingdistrict.microservices.userservice.KeyCloakService;
import com.shoppingdistrict.microservices.userservice.repository.CustomerRepository;
import com.shoppingdistrict.microservices.userservice.repository.PaidSubscriptionRepository;

@Component
@Transactional
public class MyScheduleBean {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private PaidSubscriptionRepository paidSubsRepo;

	@Autowired
	private CustomerRepository customerRepo;

	@Autowired
	private KeyCloakService keyCloakService;

	@Scheduled(fixedRate = 60000)
	public void performTask() throws Exception {
		logger.info("Daily Schedule task for deactivating expired premium user running.");
		processExpiredSubscriptions();
		logger.info("Exiting from Daily Schedule task for deactivating expired premium");
	}

	/**
	 * This method has to be transactional so that lazy loaded association inside
	 * the same scope of Hibernate session. In this case paidSubscription.getUser().
	 * 
	 * @throws Exception throw Exception to down stream where exceptions handling
	 *                   module will handle it.
	 */
	@Transactional
	public void processExpiredSubscriptions() throws Exception {
		logger.info("Entry to processExpiredSubscriptions");
		List<PaidSubscription> expiredActiveSubList = paidSubsRepo.findActiveSubscriptionWithExpiredDate();
		int size = expiredActiveSubList.size();
		if (size > 0) {
			String accessToken = keyCloakService.getKeyCloakManagementAccessToken();
			logger.info("Number of expired paid Subscriptions with Active status found {}", size);
			logger.info("Deactivating them");
			for (PaidSubscription paidSubscription : expiredActiveSubList) {

				String userId = keyCloakService.getKeycloakUserIdByUsername(paidSubscription.getUser().getUsername(),
						accessToken);
				updateSubscriptionAndUserRole(paidSubscription.getUser().getId());
				keyCloakService.evictUserFromKeycloakCache(accessToken, userId);
			}
		} else {
			logger.info("Number of expired paid Subscriptions with Active status found {}", size);
		}

		logger.info("Exiting from processExpiredSubscriptions");
	}

	/**
	 * This method is used to deactivate user paid subscription account from
	 * Paid_Subscription table as well as update user role to CUSTOMER from PREMIUM
	 * in Users table. This two operations are going to be run as a transaction to
	 * avoid conflicting data between these two tables.
	 * 
	 * @param userId - User Id of User who record is going to be updated.
	 */
	@Transactional
	private void updateSubscriptionAndUserRole(int userId) {
		logger.info("Entry to updateSubscriptionAndUserRole {}", userId);
		try {
			paidSubsRepo.deactivatePaidSubsByUserId(userId);
			customerRepo.updateUserRoleById(userId, "CUSTOMER");
			logger.info("Exiting from updateSubscriptionAndUserRole, User Premium role has been de-activated {}",
					userId);
		} catch (Exception exc) {
			logger.error(
					"Error occurred while deactivating Premium User role in daily Schedule task and exception is {}",
					exc);
			throw new RuntimeException(
					"Error occurred while deactivating Premium User role in daily Schedule task and exception is {}",
					exc);
		}

	}

}
