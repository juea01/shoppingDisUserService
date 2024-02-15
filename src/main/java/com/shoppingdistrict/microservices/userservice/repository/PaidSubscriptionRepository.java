package com.shoppingdistrict.microservices.userservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.shoppingdistrict.microservices.model.model.PaidSubscription;

public interface PaidSubscriptionRepository extends JpaRepository<PaidSubscription, Integer>{
	
	PaidSubscription findByUserId(int id);
	PaidSubscription findByThirdPartyUserId(String stripeCustomerId);
	
	@Query("SELECT ps FROM PaidSubscription ps WHERE DATE(ps.expiryDate) <= CURRENT_DATE AND ps.activeSubscription = 1")
	List<PaidSubscription> findActiveSubscriptionWithExpiredDate();
	
	@Modifying
	@Query("UPDATE PaidSubscription ps SET ps.activeSubscription = 0 WHERE ps.user.id = ?1")
	void deactivatePaidSubsByUserId(int id);

}
